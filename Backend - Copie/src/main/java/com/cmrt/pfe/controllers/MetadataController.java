package com.cmrt.pfe.controllers;

import com.cmrt.pfe.models.enums.ChangeStatus;
import com.cmrt.pfe.models.enums.ChangeType;
import com.cmrt.pfe.models.enums.Customer;
import com.cmrt.pfe.models.enums.Departement;
import com.cmrt.pfe.models.enums.DocumentStatus;
import com.cmrt.pfe.models.enums.DocumentType;
import com.cmrt.pfe.models.enums.IssueCategory;
import com.cmrt.pfe.models.enums.IssueStatus;
import com.cmrt.pfe.models.enums.Poste;
import com.cmrt.pfe.models.enums.Priority;
import com.cmrt.pfe.models.enums.ProductFamily;
import com.cmrt.pfe.models.enums.ProductStatus;
import com.cmrt.pfe.models.enums.ProjectType;
import com.cmrt.pfe.models.enums.ReservationStatus;
import com.cmrt.pfe.models.enums.ResourceStatus;
import com.cmrt.pfe.models.enums.ResourceType;
import com.cmrt.pfe.models.enums.Role;
import com.cmrt.pfe.models.enums.ServiceUnit;
import com.cmrt.pfe.models.enums.Severity;
import com.cmrt.pfe.models.enums.StageStatus;
import com.cmrt.pfe.models.enums.StageType;
import com.cmrt.pfe.models.enums.TaskStatus;
import com.cmrt.pfe.security.PublicEndpoint;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes the reference data the client builds its dropdowns from, so adding an issue
 * category or a document type never means editing the Angular code as well.
 */
@PublicEndpoint
@RestController
@RequestMapping("/api/metadata")
public class MetadataController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> all() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("roles", names(Role.values()));
        metadata.put("departements", names(Departement.values()));
        metadata.put("postes", names(Poste.values()));
        metadata.put("services", names(ServiceUnit.values()));
        metadata.put("productFamilies", names(ProductFamily.values()));
        metadata.put("customers", names(Customer.values()));
        metadata.put("projectTypes", names(ProjectType.values()));
        metadata.put("productStatuses", names(ProductStatus.values()));
        metadata.put("stageStatuses", names(StageStatus.values()));
        metadata.put("priorities", names(Priority.values()));
        metadata.put("severities", names(Severity.values()));
        metadata.put("taskStatuses", names(TaskStatus.values()));
        metadata.put("issueStatuses", names(IssueStatus.values()));
        metadata.put("issueCategories", names(IssueCategory.values()));
        metadata.put("changeTypes", names(ChangeType.values()));
        metadata.put("changeStatuses", names(ChangeStatus.values()));
        metadata.put("documentTypes", names(DocumentType.values()));
        metadata.put("documentStatuses", names(DocumentStatus.values()));
        metadata.put("resourceTypes", names(ResourceType.values()));
        metadata.put("resourceStatuses", names(ResourceStatus.values()));
        metadata.put("reservationStatuses", names(ReservationStatus.values()));
        metadata.put("pipeline", pipeline());
        return ResponseEntity.ok(metadata);
    }

    /** The pipeline template, including each gate's deliverables and approver roles. */
    @GetMapping("/pipeline")
    public ResponseEntity<List<Map<String, Object>>> pipelineTemplate() {
        return ResponseEntity.ok(pipeline());
    }

    private List<Map<String, Object>> pipeline() {
        return StageType.pipeline().stream().map(stage -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", stage.name());
            entry.put("label", stage.getLabel());
            entry.put("order", stage.getOrder());
            entry.put("defaultOwnerRole", stage.getDefaultOwnerRole().name());
            entry.put("nominalDurationDays", stage.getNominalDurationDays());
            entry.put("requiredDeliverables", stage.getRequiredDeliverables().stream().map(Enum::name).toList());
            entry.put("approverRoles", stage.getApproverRoles().stream().map(Enum::name).toList());
            return entry;
        }).toList();
    }

    private List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
