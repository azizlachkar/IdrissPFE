package com.cmrt.pfe.services;

import com.cmrt.pfe.exceptions.ApiException;
import com.cmrt.pfe.models.Approval;
import com.cmrt.pfe.models.Product;
import com.cmrt.pfe.models.ProjectStage;
import com.cmrt.pfe.models.TechnicalDocument;
import com.cmrt.pfe.models.User;
import com.cmrt.pfe.models.enums.ApprovalDecision;
import com.cmrt.pfe.models.enums.AuditAction;
import com.cmrt.pfe.models.enums.DocumentStatus;
import com.cmrt.pfe.models.enums.DocumentType;
import com.cmrt.pfe.models.enums.IssueStatus;
import com.cmrt.pfe.models.enums.NotificationType;
import com.cmrt.pfe.models.enums.ProductStatus;
import com.cmrt.pfe.models.enums.Role;
import com.cmrt.pfe.models.enums.Severity;
import com.cmrt.pfe.models.enums.StageStatus;
import com.cmrt.pfe.models.enums.StageType;
import com.cmrt.pfe.repositories.DocumentRepository;
import com.cmrt.pfe.repositories.IssueRepository;
import com.cmrt.pfe.repositories.ProductRepository;
import com.cmrt.pfe.repositories.ProjectStageRepository;
import com.cmrt.pfe.repositories.UserRepository;
import com.cmrt.pfe.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The industrialisation workflow engine.
 * <p>
 * It owns every rule that decides whether a product may move forward: which gate is
 * active, whether its deliverables exist, whether the required roles have signed off,
 * whether an open blockage freezes it, and what the resulting progress figure is.
 * Controllers never mutate a {@link ProjectStage} directly - they ask this service, so
 * the same rules apply no matter which screen triggered the transition.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowService {

    private final ProjectStageRepository stageRepository;
    private final ProductRepository productRepository;
    private final DocumentRepository documentRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    private static final Set<IssueStatus> OPEN_ISSUE_STATUSES =
            EnumSet.of(IssueStatus.OPEN, IssueStatus.ACKNOWLEDGED, IssueStatus.IN_PROGRESS);

    // ------------------------------------------------------------------
    // Pipeline creation
    // ------------------------------------------------------------------

    /**
     * Builds the full pipeline for a freshly created product from the {@link StageType}
     * template: owners derived from the product's assignments, deadlines laid out in
     * working days from the start date, and the approval slots each gate requires.
     */
    public List<ProjectStage> initializePipeline(Product product, AuthPrincipal actor) {
        stageRepository.deleteByProductId(product.getId());

        List<StageType> template = StageType.pipelineFor(product.getProjectType());
        LocalDate cursor = product.getStartDate() != null ? product.getStartDate() : LocalDate.now();

        List<ProjectStage> stages = new ArrayList<>();
        for (StageType type : template) {
            LocalDate plannedStart = cursor;
            LocalDate plannedEnd = addWorkingDays(plannedStart, type.getNominalDurationDays());

            stages.add(ProjectStage.builder()
                    .productId(product.getId())
                    .stageType(type)
                    .order(type.getOrder())
                    .status(StageStatus.NOT_STARTED)
                    .ownerId(resolveOwner(product, type))
                    .plannedStart(plannedStart)
                    .plannedEnd(plannedEnd)
                    .requiredDeliverables(new ArrayList<>(type.getRequiredDeliverables()))
                    .approvals(buildApprovalSlots(type))
                    .build());

            // Next gate starts the working day after this one is due.
            cursor = addWorkingDays(plannedEnd, 1);
        }

        List<ProjectStage> saved = stageRepository.saveAll(stages);
        auditService.record(actor, AuditAction.CREATE, "ProjectStage", product.getId(), product.getId(),
                "Pipeline initialise avec " + saved.size() + " jalons");
        recompute(product.getId());
        return saved;
    }

    private List<Approval> buildApprovalSlots(StageType type) {
        return type.getApproverRoles().stream()
                .map(role -> Approval.builder().requiredRole(role).decision(ApprovalDecision.PENDING).build())
                .toList()
                .stream()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    /**
     * Maps a gate's default owner role onto the people actually assigned to the product,
     * falling back to any active holder of that role, then to the project lead.
     */
    private String resolveOwner(Product product, StageType type) {
        String assigned = switch (type.getDefaultOwnerRole()) {
            case METHODISTE -> product.getMethodisteId();
            case QUALITICIEN -> product.getQualiticienId();
            case CHEF_PROJET -> product.getChefProjetId();
            default -> null;
        };
        if (assigned != null) {
            return assigned;
        }
        return userRepository.findByRole(type.getDefaultOwnerRole()).stream()
                .filter(u -> Boolean.TRUE.equals(u.getActive()))
                .map(User::getId)
                .findFirst()
                .orElse(product.getChefProjetId());
    }

    /** Calendar arithmetic that skips weekends, so a "5 day" gate means five working days. */
    static LocalDate addWorkingDays(LocalDate start, int days) {
        LocalDate date = start;
        int added = 0;
        while (added < days) {
            date = date.plusDays(1);
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return date;
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    public List<ProjectStage> stagesOf(String productId) {
        return stageRepository.findByProductIdOrderByOrderAsc(productId);
    }

    public ProjectStage stage(String productId, StageType stageType) {
        return stageRepository.findByProductIdAndStageType(productId, stageType)
                .orElseThrow(() -> ApiException.notFound("Jalon " + stageType));
    }

    /** Deliverable types still missing or not yet approved for a gate. */
    public List<DocumentType> missingDeliverables(String productId, StageType stageType) {
        ProjectStage stage = stage(productId, stageType);
        if (stage.getRequiredDeliverables() == null || stage.getRequiredDeliverables().isEmpty()) {
            return List.of();
        }
        Set<DocumentType> approved = documentRepository.findByProductId(productId).stream()
                .filter(d -> d.getStatus() == DocumentStatus.APPROVED)
                .map(TechnicalDocument::getType)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        return stage.getRequiredDeliverables().stream()
                .filter(required -> !approved.contains(required))
                .toList();
    }

    // ------------------------------------------------------------------
    // Transitions
    // ------------------------------------------------------------------

    /** Opens a gate. The previous gate must be closed first - the pipeline is strictly ordered. */
    public ProjectStage startStage(String productId, StageType stageType, AuthPrincipal actor) {
        ProjectStage stage = stage(productId, stageType);
        if (stage.getStatus() != StageStatus.NOT_STARTED && stage.getStatus() != StageStatus.REJECTED) {
            throw ApiException.conflict("Ce jalon est deja demarre");
        }
        requirePreviousStageClosed(productId, stage);

        stage.setStatus(StageStatus.IN_PROGRESS);
        stage.setActualStart(LocalDateTime.now());
        stage.setUpdatedAt(LocalDateTime.now());
        ProjectStage saved = stageRepository.save(stage);

        Product product = product(productId);
        auditService.record(actor, AuditAction.STATUS_CHANGE, "ProjectStage", stage.getId(), productId,
                "Demarrage du jalon " + stageType.getLabel());
        notificationService.notify(stage.getOwnerId(), NotificationType.STAGE_STARTED, Severity.MINOR,
                "Jalon demarre : " + stageType.getLabel(),
                "Le jalon " + stageType.getLabel() + " du produit " + product.getReference() + " vous est confie.",
                "/products/" + productId, actor);

        recompute(productId);
        return saved;
    }

    private void requirePreviousStageClosed(String productId, ProjectStage stage) {
        Optional<ProjectStage> previous = stagesOf(productId).stream()
                .filter(s -> s.getOrder() < stage.getOrder())
                .max(Comparator.comparingInt(ProjectStage::getOrder));
        if (previous.isPresent() && !previous.get().isTerminal()) {
            throw ApiException.conflict("Le jalon precedent ("
                    + previous.get().getStageType().getLabel() + ") doit etre cloture avant de demarrer celui-ci");
        }
    }

    /**
     * Requests sign-off. This is the real gate: every required deliverable must be an
     * approved document and no blocking issue may be open on the stage.
     */
    public ProjectStage submitForApproval(String productId, StageType stageType, AuthPrincipal actor) {
        ProjectStage stage = stage(productId, stageType);
        if (stage.getStatus() != StageStatus.IN_PROGRESS && stage.getStatus() != StageStatus.BLOCKED) {
            throw ApiException.conflict("Seul un jalon en cours peut etre soumis a validation");
        }

        List<DocumentType> missing = missingDeliverables(productId, stageType);
        if (!missing.isEmpty()) {
            throw ApiException.conflict("Livrables manquants ou non approuves : "
                    + missing.stream().map(Enum::name).collect(java.util.stream.Collectors.joining(", ")));
        }
        long blocking = issueRepository
                .findByProductIdAndStageTypeAndStatusIn(productId, stageType, OPEN_ISSUE_STATUSES).stream()
                .filter(com.cmrt.pfe.models.Issue::isBlocksStage)
                .count();
        if (blocking > 0) {
            throw ApiException.conflict("Ce jalon porte " + blocking
                    + " blocage(s) non resolu(s) : impossible de le soumettre a validation");
        }

        stage.setStatus(StageStatus.PENDING_APPROVAL);
        stage.setCompletionPercent(100);
        stage.setUpdatedAt(LocalDateTime.now());
        // A new review round starts from clean sign-offs.
        stage.getApprovals().forEach(a -> {
            a.setDecision(ApprovalDecision.PENDING);
            a.setApproverId(null);
            a.setApproverName(null);
            a.setDecidedAt(null);
            a.setComment(null);
        });
        ProjectStage saved = stageRepository.save(stage);

        Product product = product(productId);
        auditService.record(actor, AuditAction.STATUS_CHANGE, "ProjectStage", stage.getId(), productId,
                "Jalon " + stageType.getLabel() + " soumis a validation");
        notificationService.notifyRoles(
                stage.getApprovals().stream().map(Approval::getRequiredRole).toList(),
                NotificationType.APPROVAL_REQUESTED, Severity.MAJOR,
                "Validation demandee : " + stageType.getLabel(),
                "Le jalon " + stageType.getLabel() + " du produit " + product.getReference()
                        + " attend votre approbation.",
                "/products/" + productId, actor);

        recompute(productId);
        return saved;
    }

    /**
     * Records one role's sign-off. The gate only closes once every required role has
     * approved; the next gate then opens automatically.
     */
    public ProjectStage approve(String productId, StageType stageType, String comment, AuthPrincipal actor) {
        ProjectStage stage = stage(productId, stageType);
        if (stage.getStatus() != StageStatus.PENDING_APPROVAL) {
            throw ApiException.conflict("Ce jalon n'est pas en attente de validation");
        }

        Approval slot = slotFor(stage, actor);
        slot.setDecision(ApprovalDecision.APPROVED);
        slot.setApproverId(actor.userId());
        slot.setApproverName(actor.name());
        slot.setComment(comment);
        slot.setDecidedAt(LocalDateTime.now());
        stage.setUpdatedAt(LocalDateTime.now());

        boolean allApproved = stage.getApprovals().stream()
                .allMatch(a -> a.getDecision() == ApprovalDecision.APPROVED);

        Product product = product(productId);
        auditService.record(actor, AuditAction.APPROVE, "ProjectStage", stage.getId(), productId,
                "Approbation du jalon " + stageType.getLabel()
                        + " par " + actor.name() + " (" + slot.getRequiredRole() + ")");

        if (allApproved) {
            stage.setStatus(StageStatus.COMPLETED);
            stage.setActualEnd(LocalDateTime.now());
            stage.setCompletionPercent(100);
            stage.setBlocked(false);
            stage.setBlockReason(null);
        }
        ProjectStage saved = stageRepository.save(stage);

        if (allApproved) {
            notificationService.notifyAll(
                    // Stream.of, not List.of: these owners may be unassigned.
                    Stream.of(product.getChefProjetId(), product.getMethodisteId(), product.getQualiticienId())
                            .filter(java.util.Objects::nonNull).toList(),
                    NotificationType.STAGE_COMPLETED, Severity.MINOR,
                    "Jalon cloture : " + stageType.getLabel(),
                    "Le jalon " + stageType.getLabel() + " du produit " + product.getReference() + " est valide.",
                    "/products/" + productId, actor);
            openNextStage(product, stage, actor);
        } else {
            notificationService.notify(stage.getOwnerId(), NotificationType.APPROVAL_GRANTED, Severity.MINOR,
                    "Approbation enregistree : " + stageType.getLabel(),
                    actor.name() + " a approuve le jalon. En attente des autres validations.",
                    "/products/" + productId, actor);
        }

        recompute(productId);
        return saved;
    }

    /** Sends a gate back to its owner with a reason; the sign-off round restarts from zero. */
    public ProjectStage reject(String productId, StageType stageType, String comment, AuthPrincipal actor) {
        ProjectStage stage = stage(productId, stageType);
        if (stage.getStatus() != StageStatus.PENDING_APPROVAL) {
            throw ApiException.conflict("Ce jalon n'est pas en attente de validation");
        }
        if (comment == null || comment.isBlank()) {
            throw ApiException.badRequest("Un motif est obligatoire pour refuser un jalon");
        }

        Approval slot = slotFor(stage, actor);
        slot.setDecision(ApprovalDecision.REJECTED);
        slot.setApproverId(actor.userId());
        slot.setApproverName(actor.name());
        slot.setComment(comment);
        slot.setDecidedAt(LocalDateTime.now());

        stage.setStatus(StageStatus.IN_PROGRESS);
        stage.setCompletionPercent(60);
        stage.setNotes(comment);
        stage.setUpdatedAt(LocalDateTime.now());
        ProjectStage saved = stageRepository.save(stage);

        Product product = product(productId);
        auditService.record(actor, AuditAction.REJECT, "ProjectStage", stage.getId(), productId,
                "Refus du jalon " + stageType.getLabel() + " : " + comment);
        notificationService.notify(stage.getOwnerId(), NotificationType.APPROVAL_REJECTED, Severity.CRITICAL,
                "Jalon refuse : " + stageType.getLabel(),
                actor.name() + " a refuse le jalon du produit " + product.getReference() + ". Motif : " + comment,
                "/products/" + productId, actor);

        recompute(productId);
        return saved;
    }

    /** The approval slot this caller is entitled to fill. */
    private Approval slotFor(ProjectStage stage, AuthPrincipal actor) {
        Role role = actor.roleEnum();
        Optional<Approval> own = stage.getApprovals().stream()
                .filter(a -> a.getRequiredRole() == role && a.isPending())
                .findFirst();
        if (own.isPresent()) {
            return own.get();
        }
        // An administrator can unblock a review when the nominated approver is unavailable.
        if (actor.isAdmin()) {
            return stage.getApprovals().stream()
                    .filter(Approval::isPending)
                    .findFirst()
                    .orElseThrow(() -> ApiException.conflict("Toutes les validations sont deja enregistrees"));
        }
        boolean alreadyDecided = stage.getApprovals().stream()
                .anyMatch(a -> a.getRequiredRole() == role && !a.isPending());
        throw ApiException.forbidden(alreadyDecided
                ? "Vous avez deja statue sur ce jalon"
                : "Votre role n'est pas requis pour valider ce jalon");
    }

    /** Opens the following gate automatically, or closes the project when this was the last one. */
    private void openNextStage(Product product, ProjectStage completed, AuthPrincipal actor) {
        Optional<ProjectStage> next = stagesOf(product.getId()).stream()
                .filter(s -> s.getOrder() > completed.getOrder())
                .filter(s -> s.getStatus() == StageStatus.NOT_STARTED)
                .min(Comparator.comparingInt(ProjectStage::getOrder));

        if (next.isEmpty()) {
            product.setStatus(ProductStatus.MASS_PRODUCTION);
            product.setActualSopDate(LocalDate.now());
            product.setUpdatedAt(LocalDateTime.now());
            productRepository.save(product);
            auditService.record(actor, AuditAction.STATUS_CHANGE, "Product", product.getId(), product.getId(),
                    "Pipeline termine : passage en production serie");
            notificationService.notifyAll(
                    // Stream.of, not List.of: these owners may be unassigned.
                    Stream.of(product.getChefProjetId(), product.getMethodisteId(), product.getQualiticienId())
                            .filter(java.util.Objects::nonNull).toList(),
                    NotificationType.STAGE_COMPLETED, Severity.MAJOR,
                    "Produit lance en serie",
                    "Le produit " + product.getReference() + " a franchi tous les jalons et passe en production serie.",
                    "/products/" + product.getId(), actor);
            return;
        }

        ProjectStage stage = next.get();
        stage.setStatus(StageStatus.IN_PROGRESS);
        stage.setActualStart(LocalDateTime.now());
        stage.setUpdatedAt(LocalDateTime.now());
        stageRepository.save(stage);

        notificationService.notify(stage.getOwnerId(), NotificationType.STAGE_STARTED, Severity.MAJOR,
                "Nouveau jalon a traiter : " + stage.getStageType().getLabel(),
                "Le jalon precedent est valide. Le jalon " + stage.getStageType().getLabel()
                        + " du produit " + product.getReference() + " vous est confie.",
                "/products/" + product.getId(), actor);
    }

    /** Administrator override: mark a gate as not applicable for this product. */
    public ProjectStage skipStage(String productId, StageType stageType, String reason, AuthPrincipal actor) {
        ProjectStage stage = stage(productId, stageType);
        if (stage.isTerminal()) {
            throw ApiException.conflict("Ce jalon est deja cloture");
        }
        stage.setStatus(StageStatus.SKIPPED);
        stage.setNotes(reason);
        stage.setActualEnd(LocalDateTime.now());
        stage.setUpdatedAt(LocalDateTime.now());
        ProjectStage saved = stageRepository.save(stage);

        auditService.record(actor, AuditAction.STATUS_CHANGE, "ProjectStage", stage.getId(), productId,
                "Jalon " + stageType.getLabel() + " ignore : " + reason);
        openNextStage(product(productId), stage, actor);
        recompute(productId);
        return saved;
    }

    /** Owner-side edits: planning dates, progress and working notes. */
    public ProjectStage updateStage(String productId, StageType stageType, ProjectStage changes, AuthPrincipal actor) {
        ProjectStage stage = stage(productId, stageType);
        if (changes.getPlannedStart() != null) stage.setPlannedStart(changes.getPlannedStart());
        if (changes.getPlannedEnd() != null) stage.setPlannedEnd(changes.getPlannedEnd());
        if (changes.getOwnerId() != null) stage.setOwnerId(changes.getOwnerId());
        if (changes.getNotes() != null) stage.setNotes(changes.getNotes());
        if (changes.getCompletionPercent() > 0) {
            stage.setCompletionPercent(Math.min(100, Math.max(0, changes.getCompletionPercent())));
        }
        if (changes.getPlannedStart() != null && changes.getPlannedEnd() != null
                && changes.getPlannedEnd().isBefore(changes.getPlannedStart())) {
            throw ApiException.badRequest("La date de fin ne peut pas preceder la date de debut");
        }
        stage.setUpdatedAt(LocalDateTime.now());
        ProjectStage saved = stageRepository.save(stage);
        auditService.record(actor, AuditAction.UPDATE, "ProjectStage", stage.getId(), productId,
                "Mise a jour du jalon " + stageType.getLabel());
        recompute(productId);
        return saved;
    }

    // ------------------------------------------------------------------
    // Blocking, driven by the issue tracker
    // ------------------------------------------------------------------

    /** Called by {@link IssueService} when a blocking issue is raised or resolved. */
    public void refreshBlockingState(String productId, StageType stageType) {
        if (stageType == null) {
            recompute(productId);
            return;
        }
        stageRepository.findByProductIdAndStageType(productId, stageType).ifPresent(stage -> {
            List<com.cmrt.pfe.models.Issue> blocking = issueRepository
                    .findByProductIdAndStageTypeAndStatusIn(productId, stageType, OPEN_ISSUE_STATUSES).stream()
                    .filter(com.cmrt.pfe.models.Issue::isBlocksStage)
                    .toList();

            boolean nowBlocked = !blocking.isEmpty();
            stage.setBlocked(nowBlocked);
            stage.setBlockReason(nowBlocked ? blocking.get(0).getTitle() : null);

            if (nowBlocked && stage.getStatus() == StageStatus.IN_PROGRESS) {
                stage.setStatus(StageStatus.BLOCKED);
            } else if (!nowBlocked && stage.getStatus() == StageStatus.BLOCKED) {
                stage.setStatus(StageStatus.IN_PROGRESS);
            }
            stage.setUpdatedAt(LocalDateTime.now());
            stageRepository.save(stage);
        });
        recompute(productId);
    }

    // ------------------------------------------------------------------
    // Derived product state
    // ------------------------------------------------------------------

    /**
     * Recomputes the cached fields the boards and dashboards read: overall progress, the
     * gate currently in play, and whether anything is blocked. Called after every
     * transition so those views never have to aggregate on read.
     */
    public Product recompute(String productId) {
        Product product = product(productId);
        List<ProjectStage> stages = stagesOf(productId);
        if (stages.isEmpty()) {
            return product;
        }

        // Each gate weighs the same; an open gate contributes its own completion figure.
        double total = 0;
        for (ProjectStage stage : stages) {
            total += switch (stage.getStatus()) {
                case COMPLETED, SKIPPED -> 100;
                case PENDING_APPROVAL -> 90;
                case IN_PROGRESS, BLOCKED -> Math.max(stage.getCompletionPercent(), 10);
                default -> 0;
            };
        }
        product.setProgressPercent((int) Math.round(total / stages.size()));

        product.setCurrentStage(stages.stream()
                .filter(s -> !s.isTerminal())
                .min(Comparator.comparingInt(ProjectStage::getOrder))
                .map(ProjectStage::getStageType)
                .orElse(stages.get(stages.size() - 1).getStageType()));

        product.setBlocked(stages.stream().anyMatch(ProjectStage::isBlocked));

        // Keep the lifecycle status in step with pipeline reality.
        boolean allDone = stages.stream().allMatch(ProjectStage::isTerminal);
        if (allDone && product.getStatus() != ProductStatus.MASS_PRODUCTION) {
            product.setStatus(ProductStatus.MASS_PRODUCTION);
        } else if (!allDone && product.getStatus() == ProductStatus.DRAFT
                && stages.stream().anyMatch(s -> s.getStatus() != StageStatus.NOT_STARTED)) {
            product.setStatus(ProductStatus.IN_DEVELOPMENT);
        }

        product.setUpdatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    /** Every gate past its planned end date and not yet closed. */
    public List<ProjectStage> overdueStages() {
        return stageRepository.findByPlannedEndBeforeAndStatusNotIn(LocalDate.now(),
                EnumSet.of(StageStatus.COMPLETED, StageStatus.SKIPPED));
    }

    private Product product(String productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Produit"));
    }
}
