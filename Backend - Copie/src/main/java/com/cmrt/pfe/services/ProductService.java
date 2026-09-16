package com.cmrt.pfe.services;

import com.cmrt.pfe.dto.ProductDtos;
import com.cmrt.pfe.exceptions.ApiException;
import com.cmrt.pfe.models.Approval;
import com.cmrt.pfe.models.Issue;
import com.cmrt.pfe.models.Product;
import com.cmrt.pfe.models.ProjectStage;
import com.cmrt.pfe.models.TechnicalDocument;
import com.cmrt.pfe.models.enums.AuditAction;
import com.cmrt.pfe.models.enums.ChangeStatus;
import com.cmrt.pfe.models.enums.DocumentType;
import com.cmrt.pfe.models.enums.IssueStatus;
import com.cmrt.pfe.models.enums.NotificationType;
import com.cmrt.pfe.models.enums.ProjectType;
import com.cmrt.pfe.models.enums.Severity;
import com.cmrt.pfe.models.enums.StageStatus;
import com.cmrt.pfe.models.enums.TaskStatus;
import com.cmrt.pfe.repositories.DocumentRepository;
import com.cmrt.pfe.repositories.EngineeringChangeRepository;
import com.cmrt.pfe.repositories.IssueRepository;
import com.cmrt.pfe.repositories.ProductRepository;
import com.cmrt.pfe.repositories.ProjectStageRepository;
import com.cmrt.pfe.repositories.TaskRepository;
import com.cmrt.pfe.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProjectStageRepository stageRepository;
    private final IssueRepository issueRepository;
    private final TaskRepository taskRepository;
    private final DocumentRepository documentRepository;
    private final EngineeringChangeRepository changeRepository;
    private final WorkflowService workflowService;
    private final StorageService storageService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    private static final Set<IssueStatus> OPEN_ISSUES =
            EnumSet.of(IssueStatus.OPEN, IssueStatus.ACKNOWLEDGED, IssueStatus.IN_PROGRESS);
    private static final Set<TaskStatus> CLOSED_TASKS =
            EnumSet.of(TaskStatus.DONE, TaskStatus.CANCELLED);

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    public List<ProductDtos.ProductView> findAll() {
        return toViews(productRepository.findAll());
    }

    public List<ProductDtos.ProductView> findByType(ProjectType projectType) {
        return toViews(productRepository.findByProjectType(projectType));
    }

    public List<ProductDtos.ProductView> search(String term) {
        if (term == null || term.isBlank()) {
            return findAll();
        }
        return toViews(productRepository.search(term.trim()));
    }

    public List<ProductDtos.ProductView> assignedTo(String userId) {
        return toViews(productRepository.findByAnyOwner(userId));
    }

    public Product entity(String id) {
        return productRepository.findById(id).orElseThrow(() -> ApiException.notFound("Produit"));
    }

    public ProductDtos.ProductView view(String id) {
        return toViews(List.of(entity(id))).get(0);
    }

    /** Header, pipeline and counters for the product workspace. */
    public ProductDtos.ProductDetail detail(String id) {
        Product product = entity(id);
        List<ProjectStage> stages = workflowService.stagesOf(id);

        Map<String, String> owners = userService.nameLookup(
                stages.stream().map(ProjectStage::getOwnerId).filter(Objects::nonNull).toList());

        List<Issue> openIssues = issueRepository.findByProductIdAndStatusIn(id, OPEN_ISSUES);

        List<ProductDtos.StageView> stageViews = stages.stream()
                .map(stage -> toStageView(stage, owners, openIssues))
                .toList();

        return new ProductDtos.ProductDetail(
                view(id),
                stageViews,
                openIssues.size(),
                taskRepository.findByProductId(id).stream().filter(t -> !CLOSED_TASKS.contains(t.getStatus())).count(),
                documentRepository.findByProductId(id).size(),
                changeRepository.findByProductId(id).stream()
                        .filter(c -> c.getStatus() != ChangeStatus.CLOSED && c.getStatus() != ChangeStatus.REJECTED)
                        .count());
    }

    private ProductDtos.StageView toStageView(ProjectStage stage, Map<String, String> owners, List<Issue> openIssues) {
        List<DocumentType> missing = stage.getStatus() == StageStatus.NOT_STARTED
                ? List.of()
                : workflowService.missingDeliverables(stage.getProductId(), stage.getStageType());

        return new ProductDtos.StageView(
                stage.getId(),
                stage.getStageType(),
                stage.getStageType().getLabel(),
                stage.getOrder(),
                stage.getStatus().name(),
                stage.getOwnerId(),
                owners.getOrDefault(stage.getOwnerId(), "Non affecte"),
                stage.getPlannedStart(),
                stage.getPlannedEnd(),
                stage.getActualStart() != null ? stage.getActualStart().toString() : null,
                stage.getActualEnd() != null ? stage.getActualEnd().toString() : null,
                stage.getCompletionPercent(),
                stage.isOverdue(),
                stage.isBlocked(),
                stage.getBlockReason(),
                stage.getNotes(),
                stage.getRequiredDeliverables().stream().map(Enum::name).toList(),
                missing.stream().map(Enum::name).toList(),
                stage.getApprovals().stream().map(this::toApprovalView).toList(),
                openIssues.stream().filter(i -> i.getStageType() == stage.getStageType()).count());
    }

    private ProductDtos.ApprovalView toApprovalView(Approval approval) {
        return new ProductDtos.ApprovalView(
                approval.getRequiredRole() != null ? approval.getRequiredRole().name() : null,
                approval.getDecision() != null ? approval.getDecision().name() : null,
                approval.getApproverId(),
                approval.getApproverName(),
                approval.getComment(),
                approval.getDecidedAt() != null ? approval.getDecidedAt().toString() : null);
    }

    /**
     * Builds the list rows. Owner names, issue counts and task counts are resolved in
     * three bulk queries rather than per product, so a board of 200 products is still
     * four round-trips.
     */
    private List<ProductDtos.ProductView> toViews(List<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }
        List<String> productIds = products.stream().map(Product::getId).toList();

        Map<String, String> names = userService.nameLookup(products.stream()
                .flatMap(p -> Stream.of(p.getMethodisteId(), p.getQualiticienId(), p.getChefProjetId()))
                .filter(Objects::nonNull)
                .toList());

        Map<String, Long> openIssuesByProduct = issueRepository.findByStatusIn(OPEN_ISSUES).stream()
                .filter(i -> productIds.contains(i.getProductId()))
                .collect(Collectors.groupingBy(Issue::getProductId, Collectors.counting()));

        Map<String, Long> openTasksByProduct = taskRepository.findAll().stream()
                .filter(t -> t.getProductId() != null && productIds.contains(t.getProductId()))
                .filter(t -> !CLOSED_TASKS.contains(t.getStatus()))
                .collect(Collectors.groupingBy(com.cmrt.pfe.models.Task::getProductId, Collectors.counting()));

        Map<String, List<ProjectStage>> stagesByProduct = stageRepository.findAll().stream()
                .filter(s -> productIds.contains(s.getProductId()))
                .collect(Collectors.groupingBy(ProjectStage::getProductId));

        return products.stream().map(product -> {
            List<ProjectStage> stages = stagesByProduct.getOrDefault(product.getId(), List.of());
            int completed = (int) stages.stream().filter(ProjectStage::isTerminal).count();
            boolean atRisk = stages.stream().anyMatch(ProjectStage::isOverdue)
                    || (product.getTargetSopDate() != null
                        && product.getActualSopDate() == null
                        && product.getTargetSopDate().isBefore(LocalDate.now()));

            return new ProductDtos.ProductView(
                    product.getId(),
                    product.getReference(),
                    product.getNom(),
                    product.getDescription(),
                    product.getFamily(),
                    product.getCustomer(),
                    product.getProjectType(),
                    product.getStatus(),
                    product.getPriority(),
                    product.getMethodisteId(), names.getOrDefault(product.getMethodisteId(), "Non affecte"),
                    product.getQualiticienId(), names.getOrDefault(product.getQualiticienId(), "Non affecte"),
                    product.getChefProjetId(), names.getOrDefault(product.getChefProjetId(), "Non affecte"),
                    product.getStartDate(),
                    product.getTargetSopDate(),
                    product.getActualSopDate(),
                    product.getCurrentStage(),
                    product.getCurrentStage() != null ? product.getCurrentStage().getLabel() : null,
                    product.getProgressPercent(),
                    product.isBlocked(),
                    atRisk,
                    openIssuesByProduct.getOrDefault(product.getId(), 0L),
                    openTasksByProduct.getOrDefault(product.getId(), 0L),
                    stages.size(),
                    completed,
                    product.getAnnualVolume(),
                    product.getProgramme(),
                    product.getTags() == null ? List.of() : product.getTags());
        }).toList();
    }

    // ------------------------------------------------------------------
    // Writes
    // ------------------------------------------------------------------

    /** Creates the product and immediately lays out its pipeline. */
    public ProductDtos.ProductDetail create(ProductDtos.ProductRequest request, AuthPrincipal actor) {
        String reference = request.reference().trim().toUpperCase();
        if (productRepository.existsByReferenceIgnoreCase(reference)) {
            throw ApiException.badRequest("Un produit porte deja la reference " + reference);
        }

        Product product = new Product();
        product.setTags(new ArrayList<>());
        ProductDtos.apply(request, product);
        product.setCreatedBy(actor != null ? actor.userId() : null);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());

        Product saved = productRepository.save(product);
        workflowService.initializePipeline(saved, actor);

        auditService.record(actor, AuditAction.CREATE, "Product", saved.getId(), saved.getId(),
                "Creation du produit " + saved.getReference() + " (" + saved.getProjectType() + ")");
        notifyAssignees(saved, actor, "Vous etes affecte au produit " + saved.getReference());

        return detail(saved.getId());
    }

    public ProductDtos.ProductDetail update(String id, ProductDtos.ProductRequest request, AuthPrincipal actor) {
        Product product = entity(id);
        String newReference = request.reference().trim().toUpperCase();
        if (!newReference.equalsIgnoreCase(product.getReference())
                && productRepository.existsByReferenceIgnoreCase(newReference)) {
            throw ApiException.badRequest("Un produit porte deja la reference " + newReference);
        }

        ProjectType previousType = product.getProjectType();
        LocalDate previousStart = product.getStartDate();
        ProductDtos.apply(request, product);
        product.setUpdatedAt(LocalDateTime.now());
        Product saved = productRepository.save(product);

        // Switching NPI <-> Production changes which gates apply, so the pipeline is rebuilt.
        boolean pipelineUntouched = workflowService.stagesOf(id).stream()
                .allMatch(s -> s.getStatus() == StageStatus.NOT_STARTED);
        if (previousType != saved.getProjectType()
                || (pipelineUntouched && !java.util.Objects.equals(previousStart, saved.getStartDate()))) {
            workflowService.initializePipeline(saved, actor);
        } else {
            workflowService.recompute(id);
        }

        auditService.record(actor, AuditAction.UPDATE, "Product", id, id,
                "Mise a jour du produit " + saved.getReference());
        return detail(id);
    }

    /** Reassigns the methodiste / qualiticien / project lead and re-owns the open gates. */
    public ProductDtos.ProductDetail assign(String id, ProductDtos.AssignRequest request, AuthPrincipal actor) {
        Product product = entity(id);
        product.setMethodisteId(request.methodisteId());
        product.setQualiticienId(request.qualiticienId());
        product.setChefProjetId(request.chefProjetId());
        product.setUpdatedAt(LocalDateTime.now());
        Product saved = productRepository.save(product);

        // Gates not yet started follow the new assignment; started ones keep their owner.
        List<ProjectStage> stages = workflowService.stagesOf(id);
        stages.stream()
                .filter(s -> s.getStatus() == StageStatus.NOT_STARTED)
                .forEach(stage -> {
                    String owner = switch (stage.getStageType().getDefaultOwnerRole()) {
                        case METHODISTE -> saved.getMethodisteId();
                        case QUALITICIEN -> saved.getQualiticienId();
                        case CHEF_PROJET -> saved.getChefProjetId();
                        default -> stage.getOwnerId();
                    };
                    if (owner != null) {
                        stage.setOwnerId(owner);
                        stageRepository.save(stage);
                    }
                });

        auditService.record(actor, AuditAction.ASSIGN, "Product", id, id,
                "Affectation des responsables du produit " + saved.getReference());
        notifyAssignees(saved, actor, "Vous etes affecte au produit " + saved.getReference());
        return detail(id);
    }

    private void notifyAssignees(Product product, AuthPrincipal actor, String message) {
        notificationService.notifyAll(
                Stream.of(product.getMethodisteId(), product.getQualiticienId(), product.getChefProjetId())
                        .filter(Objects::nonNull).toList(),
                NotificationType.PRODUCT_ASSIGNED, Severity.MINOR,
                "Affectation produit", message + " - " + product.getNom(),
                "/products/" + product.getId(), actor);
    }

    /** Removes a product and everything hanging off it, stored files included. */
    public void delete(String id, AuthPrincipal actor) {
        Product product = entity(id);
        stageRepository.deleteByProductId(id);
        issueRepository.deleteByProductId(id);
        taskRepository.deleteByProductId(id);

        // Drop the files before the metadata, otherwise the stored paths are lost and
        // the uploads directory keeps growing with orphans.
        documentRepository.findByProductId(id).stream()
                .flatMap(doc -> doc.getVersions() == null ? Stream.empty() : doc.getVersions().stream())
                .map(TechnicalDocument.Version::getStoredPath)
                .filter(Objects::nonNull)
                .forEach(storageService::delete);
        documentRepository.deleteByProductId(id);

        changeRepository.deleteByProductId(id);
        productRepository.deleteById(id);
        auditService.record(actor, AuditAction.DELETE, "Product", id, id,
                "Suppression du produit " + product.getReference());
    }
}
