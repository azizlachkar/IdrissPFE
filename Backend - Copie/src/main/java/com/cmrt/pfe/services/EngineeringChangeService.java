package com.cmrt.pfe.services;

import com.cmrt.pfe.exceptions.ApiException;
import com.cmrt.pfe.models.Approval;
import com.cmrt.pfe.models.EngineeringChange;
import com.cmrt.pfe.models.Product;
import com.cmrt.pfe.models.enums.ApprovalDecision;
import com.cmrt.pfe.models.enums.AuditAction;
import com.cmrt.pfe.models.enums.ChangeStatus;
import com.cmrt.pfe.models.enums.NotificationType;
import com.cmrt.pfe.models.enums.Role;
import com.cmrt.pfe.models.enums.Severity;
import com.cmrt.pfe.repositories.EngineeringChangeRepository;
import com.cmrt.pfe.repositories.ProductRepository;
import com.cmrt.pfe.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Engineering change control (ECR / ECO).
 * <p>
 * The board of approvers is derived from the change's own impact rather than fixed:
 * a tooling change pulls in the methodiste, a test-means change pulls in technical
 * control, and anything the customer must see pulls in the project lead.
 */
@Service
@RequiredArgsConstructor
public class EngineeringChangeService {

    private final EngineeringChangeRepository changeRepository;
    private final ProductRepository productRepository;
    private final NotificationService notificationService;
    private final ReferenceGenerator referenceGenerator;
    private final AuditService auditService;

    public List<EngineeringChange> findAll() {
        return changeRepository.findAll();
    }

    public List<EngineeringChange> findByProduct(String productId) {
        return changeRepository.findByProductId(productId);
    }

    public EngineeringChange findById(String id) {
        return changeRepository.findById(id).orElseThrow(() -> ApiException.notFound("Demande de modification"));
    }

    public EngineeringChange create(EngineeringChange request, AuthPrincipal actor) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> ApiException.notFound("Produit"));
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw ApiException.badRequest("Le titre de la demande est obligatoire");
        }

        EngineeringChange change = EngineeringChange.builder()
                .reference(referenceGenerator.nextChangeReference())
                .productId(product.getId())
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .reason(request.getReason())
                .type(request.getType())
                .priority(request.getPriority())
                .status(ChangeStatus.DRAFT)
                .requesterId(actor != null ? actor.userId() : null)
                .impactDescription(request.getImpactDescription())
                .estimatedCost(request.getEstimatedCost())
                .estimatedLeadTimeDays(request.getEstimatedLeadTimeDays())
                .impactsTooling(request.isImpactsTooling())
                .impactsTestMeans(request.isImpactsTestMeans())
                .requiresCustomerApproval(request.isRequiresCustomerApproval())
                .affectedDocumentIds(request.getAffectedDocumentIds() != null
                        ? request.getAffectedDocumentIds() : new ArrayList<>())
                .effectiveDate(request.getEffectiveDate())
                .approvals(new ArrayList<>())
                .build();

        EngineeringChange saved = changeRepository.save(change);
        auditService.record(actor, AuditAction.CREATE, "EngineeringChange", saved.getId(), product.getId(),
                "Demande de modification " + saved.getReference() + " creee");
        return saved;
    }

    public EngineeringChange update(String id, EngineeringChange changes, AuthPrincipal actor) {
        EngineeringChange change = findById(id);
        if (change.getStatus() != ChangeStatus.DRAFT) {
            throw ApiException.conflict("Seule une demande au statut brouillon peut etre modifiee");
        }
        if (changes.getTitle() != null) change.setTitle(changes.getTitle());
        if (changes.getDescription() != null) change.setDescription(changes.getDescription());
        if (changes.getReason() != null) change.setReason(changes.getReason());
        if (changes.getType() != null) change.setType(changes.getType());
        if (changes.getPriority() != null) change.setPriority(changes.getPriority());
        if (changes.getImpactDescription() != null) change.setImpactDescription(changes.getImpactDescription());
        if (changes.getEstimatedCost() != null) change.setEstimatedCost(changes.getEstimatedCost());
        if (changes.getEstimatedLeadTimeDays() != null) change.setEstimatedLeadTimeDays(changes.getEstimatedLeadTimeDays());
        if (changes.getEffectiveDate() != null) change.setEffectiveDate(changes.getEffectiveDate());
        if (changes.getAffectedDocumentIds() != null) change.setAffectedDocumentIds(changes.getAffectedDocumentIds());
        change.setImpactsTooling(changes.isImpactsTooling());
        change.setImpactsTestMeans(changes.isImpactsTestMeans());
        change.setRequiresCustomerApproval(changes.isRequiresCustomerApproval());

        EngineeringChange saved = changeRepository.save(change);
        auditService.record(actor, AuditAction.UPDATE, "EngineeringChange", id, change.getProductId(),
                "Mise a jour de " + change.getReference());
        return saved;
    }

    /** Locks the request and opens the review, building the approval board from the impact. */
    public EngineeringChange submit(String id, AuthPrincipal actor) {
        EngineeringChange change = findById(id);
        if (change.getStatus() != ChangeStatus.DRAFT) {
            throw ApiException.conflict("Cette demande a deja ete soumise");
        }
        if (change.getImpactDescription() == null || change.getImpactDescription().isBlank()) {
            throw ApiException.badRequest("L'analyse d'impact est obligatoire avant soumission");
        }

        change.setApprovals(approvalBoardFor(change));
        change.setStatus(ChangeStatus.UNDER_REVIEW);
        change.setSubmittedAt(LocalDateTime.now());

        EngineeringChange saved = changeRepository.save(change);
        Product product = productRepository.findById(change.getProductId()).orElse(null);

        auditService.record(actor, AuditAction.STATUS_CHANGE, "EngineeringChange", id, change.getProductId(),
                change.getReference() + " soumise a revue");
        notificationService.notifyRoles(
                change.getApprovals().stream().map(Approval::getRequiredRole).toList(),
                NotificationType.ECR_SUBMITTED, Severity.MAJOR,
                "Demande de modification a revoir : " + change.getReference(),
                (product != null ? product.getReference() + " - " : "") + change.getTitle(),
                "/changes/" + id, actor);
        return saved;
    }

    /** Quality and the project lead always sit on the board; impact adds the rest. */
    private List<Approval> approvalBoardFor(EngineeringChange change) {
        List<Role> roles = new ArrayList<>(List.of(Role.QUALITICIEN, Role.CHEF_PROJET));
        if (change.isImpactsTooling() || change.getType() != null) {
            roles.add(Role.METHODISTE);
        }
        if (change.isImpactsTestMeans()) {
            roles.add(Role.CONTROLE_TECHNIQUE);
        }
        return roles.stream().distinct()
                .map(role -> Approval.builder().requiredRole(role).decision(ApprovalDecision.PENDING).build())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    public EngineeringChange approve(String id, String comment, AuthPrincipal actor) {
        EngineeringChange change = requireUnderReview(id);
        Approval slot = slotFor(change, actor);
        slot.setDecision(ApprovalDecision.APPROVED);
        slot.setApproverId(actor.userId());
        slot.setApproverName(actor.name());
        slot.setComment(comment);
        slot.setDecidedAt(LocalDateTime.now());

        boolean allApproved = change.getApprovals().stream()
                .allMatch(a -> a.getDecision() == ApprovalDecision.APPROVED);
        if (allApproved) {
            change.setStatus(ChangeStatus.APPROVED);
            change.setDecidedAt(LocalDateTime.now());
        }

        EngineeringChange saved = changeRepository.save(change);
        auditService.record(actor, AuditAction.APPROVE, "EngineeringChange", id, change.getProductId(),
                change.getReference() + " approuvee par " + actor.name());

        if (allApproved) {
            notificationService.notify(change.getRequesterId(), NotificationType.ECR_APPROVED, Severity.MAJOR,
                    "Modification approuvee : " + change.getReference(),
                    change.getTitle() + " - vous pouvez lancer la mise en oeuvre.",
                    "/changes/" + id, actor);
        }
        return saved;
    }

    public EngineeringChange reject(String id, String comment, AuthPrincipal actor) {
        if (comment == null || comment.isBlank()) {
            throw ApiException.badRequest("Un motif est obligatoire pour refuser une demande");
        }
        EngineeringChange change = requireUnderReview(id);
        Approval slot = slotFor(change, actor);
        slot.setDecision(ApprovalDecision.REJECTED);
        slot.setApproverId(actor.userId());
        slot.setApproverName(actor.name());
        slot.setComment(comment);
        slot.setDecidedAt(LocalDateTime.now());

        // One refusal ends the review - the requester revises and resubmits.
        change.setStatus(ChangeStatus.REJECTED);
        change.setDecidedAt(LocalDateTime.now());

        EngineeringChange saved = changeRepository.save(change);
        auditService.record(actor, AuditAction.REJECT, "EngineeringChange", id, change.getProductId(),
                change.getReference() + " refusee : " + comment);
        notificationService.notify(change.getRequesterId(), NotificationType.GENERIC, Severity.MAJOR,
                "Modification refusee : " + change.getReference(), "Motif : " + comment,
                "/changes/" + id, actor);
        return saved;
    }

    /** Closes the loop once the revised documents are in place. */
    public EngineeringChange markImplemented(String id, AuthPrincipal actor) {
        EngineeringChange change = findById(id);
        if (change.getStatus() != ChangeStatus.APPROVED) {
            throw ApiException.conflict("Seule une demande approuvee peut etre declaree mise en oeuvre");
        }
        change.setStatus(ChangeStatus.IMPLEMENTED);
        change.setImplementedAt(LocalDateTime.now());
        EngineeringChange saved = changeRepository.save(change);
        auditService.record(actor, AuditAction.STATUS_CHANGE, "EngineeringChange", id, change.getProductId(),
                change.getReference() + " mise en oeuvre");
        return saved;
    }

    public EngineeringChange close(String id, AuthPrincipal actor) {
        EngineeringChange change = findById(id);
        change.setStatus(ChangeStatus.CLOSED);
        EngineeringChange saved = changeRepository.save(change);
        auditService.record(actor, AuditAction.STATUS_CHANGE, "EngineeringChange", id, change.getProductId(),
                change.getReference() + " cloturee");
        return saved;
    }

    public void delete(String id, AuthPrincipal actor) {
        EngineeringChange change = findById(id);
        if (change.getStatus() != ChangeStatus.DRAFT) {
            throw ApiException.conflict("Seule une demande au statut brouillon peut etre supprimee");
        }
        changeRepository.deleteById(id);
        auditService.record(actor, AuditAction.DELETE, "EngineeringChange", id, change.getProductId(),
                "Suppression de " + change.getReference());
    }

    private EngineeringChange requireUnderReview(String id) {
        EngineeringChange change = findById(id);
        if (change.getStatus() != ChangeStatus.UNDER_REVIEW && change.getStatus() != ChangeStatus.SUBMITTED) {
            throw ApiException.conflict("Cette demande n'est pas en cours de revue");
        }
        return change;
    }

    private Approval slotFor(EngineeringChange change, AuthPrincipal actor) {
        Role role = actor.roleEnum();
        Optional<Approval> own = change.getApprovals().stream()
                .filter(a -> a.getRequiredRole() == role && a.isPending())
                .findFirst();
        if (own.isPresent()) {
            return own.get();
        }
        if (actor.isAdmin()) {
            return change.getApprovals().stream().filter(Approval::isPending).findFirst()
                    .orElseThrow(() -> ApiException.conflict("Toutes les validations sont deja enregistrees"));
        }
        throw ApiException.forbidden("Votre role n'est pas requis pour statuer sur cette demande");
    }
}
