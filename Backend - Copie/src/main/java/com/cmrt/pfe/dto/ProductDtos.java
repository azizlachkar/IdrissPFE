package com.cmrt.pfe.dto;

import com.cmrt.pfe.models.Product;
import com.cmrt.pfe.models.ProjectStage;
import com.cmrt.pfe.models.enums.Customer;
import com.cmrt.pfe.models.enums.Priority;
import com.cmrt.pfe.models.enums.ProductFamily;
import com.cmrt.pfe.models.enums.ProductStatus;
import com.cmrt.pfe.models.enums.ProjectType;
import com.cmrt.pfe.models.enums.StageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public final class ProductDtos {

    private ProductDtos() {
    }

    public record ProductRequest(
            @NotBlank(message = "La reference est obligatoire") String reference,
            @NotBlank(message = "Le nom est obligatoire") String nom,
            String description,
            @NotNull(message = "La famille est obligatoire") ProductFamily family,
            @NotNull(message = "Le client est obligatoire") Customer customer,
            @NotNull(message = "Le type de projet est obligatoire") ProjectType projectType,
            ProductStatus status,
            Priority priority,
            String methodisteId,
            String qualiticienId,
            String chefProjetId,
            LocalDate startDate,
            LocalDate targetSopDate,
            Integer annualVolume,
            String programme,
            List<String> tags) {
    }

    /**
     * A product enriched with the names and counters the boards display, so the client
     * renders a row without extra calls.
     */
    public record ProductView(
            String id,
            String reference,
            String nom,
            String description,
            ProductFamily family,
            Customer customer,
            ProjectType projectType,
            ProductStatus status,
            Priority priority,
            String methodisteId,
            String methodisteName,
            String qualiticienId,
            String qualiticienName,
            String chefProjetId,
            String chefProjetName,
            LocalDate startDate,
            LocalDate targetSopDate,
            LocalDate actualSopDate,
            StageType currentStage,
            String currentStageLabel,
            int progressPercent,
            boolean blocked,
            boolean atRisk,
            long openIssues,
            long openTasks,
            int totalStages,
            int completedStages,
            Integer annualVolume,
            String programme,
            List<String> tags) {
    }

    /** Full detail payload for the product workspace: header plus its pipeline. */
    public record ProductDetail(
            ProductView product,
            List<StageView> stages,
            long openIssues,
            long openTasks,
            long documents,
            long openChanges) {
    }

    /** One pipeline gate, enriched for display. */
    public record StageView(
            String id,
            StageType stageType,
            String label,
            int order,
            String status,
            String ownerId,
            String ownerName,
            LocalDate plannedStart,
            LocalDate plannedEnd,
            String actualStart,
            String actualEnd,
            int completionPercent,
            boolean overdue,
            boolean blocked,
            String blockReason,
            String notes,
            List<String> requiredDeliverables,
            List<String> missingDeliverables,
            List<ApprovalView> approvals,
            long openIssues) {
    }

    public record ApprovalView(
            String requiredRole,
            String decision,
            String approverId,
            String approverName,
            String comment,
            String decidedAt) {
    }

    public record AssignRequest(String methodisteId, String qualiticienId, String chefProjetId) {
    }

    public record StageUpdateRequest(
            LocalDate plannedStart,
            LocalDate plannedEnd,
            String ownerId,
            String notes,
            Integer completionPercent) {

        public ProjectStage toStage() {
            ProjectStage stage = new ProjectStage();
            stage.setPlannedStart(plannedStart);
            stage.setPlannedEnd(plannedEnd);
            stage.setOwnerId(ownerId);
            stage.setNotes(notes);
            stage.setCompletionPercent(completionPercent == null ? 0 : completionPercent);
            return stage;
        }
    }

    public record DecisionRequest(String comment) {
    }

    /** Applies a create/update request onto an entity, leaving untouched fields alone. */
    public static void apply(ProductRequest request, Product product) {
        product.setReference(request.reference().trim().toUpperCase());
        product.setNom(request.nom().trim());
        product.setDescription(request.description());
        product.setFamily(request.family());
        product.setCustomer(request.customer());
        product.setProjectType(request.projectType());
        product.setStatus(request.status() != null ? request.status() : ProductStatus.DRAFT);
        product.setPriority(request.priority() != null ? request.priority() : Priority.MEDIUM);
        product.setMethodisteId(request.methodisteId());
        product.setQualiticienId(request.qualiticienId());
        product.setChefProjetId(request.chefProjetId());
        product.setStartDate(request.startDate() != null ? request.startDate() : LocalDate.now());
        product.setTargetSopDate(request.targetSopDate());
        product.setAnnualVolume(request.annualVolume());
        product.setProgramme(request.programme());
        if (request.tags() != null) {
            product.setTags(request.tags());
        }
    }
}
