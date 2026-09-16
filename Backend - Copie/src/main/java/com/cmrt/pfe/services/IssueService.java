package com.cmrt.pfe.services;

import com.cmrt.pfe.exceptions.ApiException;
import com.cmrt.pfe.models.Issue;
import com.cmrt.pfe.models.Product;
import com.cmrt.pfe.models.enums.AuditAction;
import com.cmrt.pfe.models.enums.IssueStatus;
import com.cmrt.pfe.models.enums.NotificationType;
import com.cmrt.pfe.models.enums.Severity;
import com.cmrt.pfe.repositories.IssueRepository;
import com.cmrt.pfe.repositories.ProductRepository;
import com.cmrt.pfe.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The blockage tracker behind the "Reclamation" button. Raising a blocking issue freezes
 * the gate it belongs to; resolving it releases the gate again, through
 * {@link WorkflowService#refreshBlockingState}.
 */
@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueRepository issueRepository;
    private final ProductRepository productRepository;
    private final WorkflowService workflowService;
    private final NotificationService notificationService;
    private final ReferenceGenerator referenceGenerator;
    private final AuditService auditService;

    public static final Set<IssueStatus> OPEN_STATUSES =
            EnumSet.of(IssueStatus.OPEN, IssueStatus.ACKNOWLEDGED, IssueStatus.IN_PROGRESS);

    public List<Issue> findAll() {
        return issueRepository.findAll();
    }

    public List<Issue> findOpen() {
        return issueRepository.findByStatusIn(OPEN_STATUSES);
    }

    public List<Issue> findByProduct(String productId) {
        return issueRepository.findByProductId(productId);
    }

    public List<Issue> findAssignedTo(String userId) {
        return issueRepository.findByAssigneeId(userId);
    }

    public Issue findById(String id) {
        return issueRepository.findById(id).orElseThrow(() -> ApiException.notFound("Blocage"));
    }

    /**
     * Records a new blockage. Severity drives the SLA and, when the issue is flagged as
     * blocking, the owning gate is frozen immediately.
     */
    public Issue create(Issue request, AuthPrincipal actor) {
        if (request.getProductId() == null || request.getProductId().isBlank()) {
            throw ApiException.badRequest("Le produit concerne est obligatoire");
        }
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> ApiException.notFound("Produit"));
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw ApiException.badRequest("Le titre du blocage est obligatoire");
        }

        Severity severity = request.getSeverity() != null ? request.getSeverity() : Severity.MAJOR;
        int sla = slaHoursFor(severity);

        Issue issue = Issue.builder()
                .reference(referenceGenerator.nextIssueReference())
                .productId(product.getId())
                .stageType(request.getStageType() != null ? request.getStageType() : product.getCurrentStage())
                .title(request.getTitle().trim())
                .category(request.getCategory())
                .severity(severity)
                .status(IssueStatus.OPEN)
                .description(request.getDescription())
                .reporterId(actor != null ? actor.userId() : null)
                .assigneeId(request.getAssigneeId() != null ? request.getAssigneeId() : defaultAssignee(product, severity))
                .blocksStage(request.isBlocksStage())
                .slaHours(sla)
                .dueDate(request.getDueDate() != null ? request.getDueDate() : LocalDate.now().plusDays(sla / 24 + 1))
                .comments(new ArrayList<>())
                .build();

        Issue saved = issueRepository.save(issue);

        auditService.record(actor, AuditAction.CREATE, "Issue", saved.getId(), product.getId(),
                "Blocage " + saved.getReference() + " declare : " + saved.getTitle());

        Severity notifySeverity = severity == Severity.BLOCKING || severity == Severity.CRITICAL
                ? Severity.CRITICAL : Severity.MAJOR;
        // Stream.of rather than List.of: any of these owners may be unassigned, and
        // List.of rejects null elements.
        notificationService.notifyAll(
                Stream.of(saved.getAssigneeId(), product.getChefProjetId(),
                                product.getMethodisteId(), product.getQualiticienId())
                        .filter(Objects::nonNull).toList(),
                NotificationType.ISSUE_CREATED, notifySeverity,
                "Nouveau blocage : " + saved.getReference(),
                product.getReference() + " - " + saved.getTitle()
                        + " (gravite " + severity + ", reponse attendue sous " + sla + "h)",
                "/issues/" + saved.getId(), actor);

        if (saved.isBlocksStage()) {
            workflowService.refreshBlockingState(product.getId(), saved.getStageType());
        }
        return saved;
    }

    /** Response time promised per severity - the basis of the SLA indicator. */
    private int slaHoursFor(Severity severity) {
        return switch (severity) {
            case BLOCKING -> 4;
            case CRITICAL -> 8;
            case MAJOR -> 24;
            case MINOR -> 72;
        };
    }

    /** Blocking issues go to the project lead by default; the rest to the methodiste. */
    private String defaultAssignee(Product product, Severity severity) {
        if (severity == Severity.BLOCKING || severity == Severity.CRITICAL) {
            return product.getChefProjetId() != null ? product.getChefProjetId() : product.getMethodisteId();
        }
        return product.getMethodisteId() != null ? product.getMethodisteId() : product.getChefProjetId();
    }

    public Issue update(String id, Issue changes, AuthPrincipal actor) {
        Issue issue = findById(id);
        if (changes.getTitle() != null) issue.setTitle(changes.getTitle());
        if (changes.getDescription() != null) issue.setDescription(changes.getDescription());
        if (changes.getCategory() != null) issue.setCategory(changes.getCategory());
        if (changes.getSeverity() != null) {
            issue.setSeverity(changes.getSeverity());
            issue.setSlaHours(slaHoursFor(changes.getSeverity()));
        }
        if (changes.getRootCause() != null) issue.setRootCause(changes.getRootCause());
        if (changes.getCorrectiveAction() != null) issue.setCorrectiveAction(changes.getCorrectiveAction());
        if (changes.getDueDate() != null) issue.setDueDate(changes.getDueDate());
        if (changes.getStageType() != null) issue.setStageType(changes.getStageType());
        issue.setBlocksStage(changes.isBlocksStage());

        Issue saved = issueRepository.save(issue);
        auditService.record(actor, AuditAction.UPDATE, "Issue", id, issue.getProductId(),
                "Mise a jour du blocage " + issue.getReference());
        workflowService.refreshBlockingState(issue.getProductId(), issue.getStageType());
        return saved;
    }

    public Issue assign(String id, String assigneeId, AuthPrincipal actor) {
        Issue issue = findById(id);
        issue.setAssigneeId(assigneeId);
        Issue saved = issueRepository.save(issue);

        auditService.record(actor, AuditAction.ASSIGN, "Issue", id, issue.getProductId(),
                "Blocage " + issue.getReference() + " affecte");
        notificationService.notify(assigneeId, NotificationType.ISSUE_ASSIGNED, Severity.MAJOR,
                "Blocage qui vous est affecte : " + issue.getReference(),
                issue.getTitle(), "/issues/" + id, actor);
        return saved;
    }

    /**
     * Moves the blockage through its lifecycle and keeps the pipeline in step: a
     * resolved or rejected blocking issue releases the gate it was freezing.
     */
    public Issue changeStatus(String id, IssueStatus status, String comment, AuthPrincipal actor) {
        Issue issue = findById(id);
        IssueStatus previous = issue.getStatus();
        if (previous == status) {
            return issue;
        }
        if (status == IssueStatus.RESOLVED
                && (issue.getCorrectiveAction() == null || issue.getCorrectiveAction().isBlank())
                && (comment == null || comment.isBlank())) {
            throw ApiException.badRequest("Renseignez l'action corrective avant de resoudre ce blocage");
        }

        issue.setStatus(status);
        LocalDateTime now = LocalDateTime.now();
        switch (status) {
            case ACKNOWLEDGED -> issue.setAcknowledgedAt(now);
            case RESOLVED -> {
                issue.setResolvedAt(now);
                if (comment != null && !comment.isBlank() && (issue.getCorrectiveAction() == null || issue.getCorrectiveAction().isBlank())) {
                    issue.setCorrectiveAction(comment);
                }
            }
            case CLOSED -> {
                issue.setClosedAt(now);
                if (issue.getResolvedAt() == null) issue.setResolvedAt(now);
            }
            default -> { }
        }
        if (comment != null && !comment.isBlank()) {
            addCommentInternal(issue, comment, actor);
        }

        Issue saved = issueRepository.save(issue);
        auditService.record(actor, AuditAction.STATUS_CHANGE, "Issue", id, issue.getProductId(),
                "Blocage " + issue.getReference() + " : " + previous + " -> " + status,
                previous.name(), status.name());

        if (status == IssueStatus.RESOLVED || status == IssueStatus.CLOSED) {
            notificationService.notify(issue.getReporterId(), NotificationType.ISSUE_RESOLVED, Severity.MINOR,
                    "Blocage resolu : " + issue.getReference(),
                    issue.getTitle() + (issue.getCorrectiveAction() != null
                            ? " - Action : " + issue.getCorrectiveAction() : ""),
                    "/issues/" + id, actor);
        }
        workflowService.refreshBlockingState(issue.getProductId(), issue.getStageType());
        return saved;
    }

    public Issue addComment(String id, String message, AuthPrincipal actor) {
        if (message == null || message.isBlank()) {
            throw ApiException.badRequest("Le commentaire ne peut pas etre vide");
        }
        Issue issue = findById(id);
        addCommentInternal(issue, message, actor);
        Issue saved = issueRepository.save(issue);

        // Keep the other party in the loop, whichever side commented.
        String other = actor != null && actor.userId().equals(issue.getReporterId())
                ? issue.getAssigneeId() : issue.getReporterId();
        notificationService.notify(other, NotificationType.GENERIC, Severity.MINOR,
                "Nouveau commentaire : " + issue.getReference(), message, "/issues/" + id, actor);
        return saved;
    }

    private void addCommentInternal(Issue issue, String message, AuthPrincipal actor) {
        if (issue.getComments() == null) {
            issue.setComments(new ArrayList<>());
        }
        issue.getComments().add(Issue.Comment.builder()
                .authorId(actor != null ? actor.userId() : null)
                .authorName(actor != null ? actor.name() : "Systeme")
                .message(message)
                .build());
    }

    public void delete(String id, AuthPrincipal actor) {
        Issue issue = findById(id);
        issueRepository.deleteById(id);
        auditService.record(actor, AuditAction.DELETE, "Issue", id, issue.getProductId(),
                "Suppression du blocage " + issue.getReference());
        workflowService.refreshBlockingState(issue.getProductId(), issue.getStageType());
    }
}
