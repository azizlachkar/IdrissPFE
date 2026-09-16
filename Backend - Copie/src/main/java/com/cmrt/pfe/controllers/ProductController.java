package com.cmrt.pfe.controllers;

import com.cmrt.pfe.dto.ProductDtos;
import com.cmrt.pfe.models.AuditLog;
import com.cmrt.pfe.models.ProjectStage;
import com.cmrt.pfe.models.enums.ProjectType;
import com.cmrt.pfe.models.enums.Role;
import com.cmrt.pfe.models.enums.StageType;
import com.cmrt.pfe.security.AuthPrincipal;
import com.cmrt.pfe.security.CurrentUser;
import com.cmrt.pfe.security.RequireRole;
import com.cmrt.pfe.services.AuditService;
import com.cmrt.pfe.services.ProductService;
import com.cmrt.pfe.services.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Products and their pipelines. The workflow transitions live here rather than in a
 * separate controller because they are always about one product's gates.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final WorkflowService workflowService;
    private final AuditService auditService;

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    @GetMapping
    public ResponseEntity<List<ProductDtos.ProductView>> all(@RequestParam(required = false) ProjectType type,
                                                             @RequestParam(required = false) String search) {
        if (search != null && !search.isBlank()) {
            return ResponseEntity.ok(productService.search(search));
        }
        return ResponseEntity.ok(type == null ? productService.findAll() : productService.findByType(type));
    }

    /** Products the caller is responsible for - the "my projects" view. */
    @GetMapping("/mine")
    public ResponseEntity<List<ProductDtos.ProductView>> mine(@CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(productService.assignedTo(principal.userId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDtos.ProductDetail> detail(@PathVariable String id) {
        return ResponseEntity.ok(productService.detail(id));
    }

    @GetMapping("/{id}/stages")
    public ResponseEntity<List<ProductDtos.StageView>> stages(@PathVariable String id) {
        return ResponseEntity.ok(productService.detail(id).stages());
    }

    /** Full traceability timeline for one product. */
    @GetMapping("/{id}/history")
    public ResponseEntity<List<AuditLog>> history(@PathVariable String id) {
        return ResponseEntity.ok(auditService.forProduct(id));
    }

    // ------------------------------------------------------------------
    // Writes
    // ------------------------------------------------------------------

    @RequireRole({Role.ADMIN, Role.CHEF_PROJET, Role.METHODISTE})
    @PostMapping
    public ResponseEntity<ProductDtos.ProductDetail> create(@Valid @RequestBody ProductDtos.ProductRequest request,
                                                            @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(productService.create(request, principal));
    }

    @RequireRole({Role.ADMIN, Role.CHEF_PROJET, Role.METHODISTE})
    @PutMapping("/{id}")
    public ResponseEntity<ProductDtos.ProductDetail> update(@PathVariable String id,
                                                            @Valid @RequestBody ProductDtos.ProductRequest request,
                                                            @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(productService.update(id, request, principal));
    }

    @RequireRole({Role.ADMIN, Role.CHEF_PROJET})
    @PatchMapping("/{id}/assign")
    public ResponseEntity<ProductDtos.ProductDetail> assign(@PathVariable String id,
                                                            @RequestBody ProductDtos.AssignRequest request,
                                                            @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(productService.assign(id, request, principal));
    }

    @RequireRole(Role.ADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, @CurrentUser AuthPrincipal principal) {
        productService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    // Pipeline transitions
    // ------------------------------------------------------------------

    @PostMapping("/{id}/stages/{stage}/start")
    public ResponseEntity<ProjectStage> startStage(@PathVariable String id,
                                                   @PathVariable("stage") StageType stage,
                                                   @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(workflowService.startStage(id, stage, principal));
    }

    @PostMapping("/{id}/stages/{stage}/submit")
    public ResponseEntity<ProjectStage> submitStage(@PathVariable String id,
                                                    @PathVariable("stage") StageType stage,
                                                    @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(workflowService.submitForApproval(id, stage, principal));
    }

    @RequireRole({Role.ADMIN, Role.CHEF_PROJET, Role.METHODISTE, Role.QUALITICIEN,
            Role.RESPONSABLE_PRODUCTION, Role.CONTROLE_TECHNIQUE})
    @PostMapping("/{id}/stages/{stage}/approve")
    public ResponseEntity<ProjectStage> approveStage(@PathVariable String id,
                                                     @PathVariable("stage") StageType stage,
                                                     @RequestBody(required = false) ProductDtos.DecisionRequest request,
                                                     @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(workflowService.approve(id, stage,
                request != null ? request.comment() : null, principal));
    }

    @RequireRole({Role.ADMIN, Role.CHEF_PROJET, Role.METHODISTE, Role.QUALITICIEN,
            Role.RESPONSABLE_PRODUCTION, Role.CONTROLE_TECHNIQUE})
    @PostMapping("/{id}/stages/{stage}/reject")
    public ResponseEntity<ProjectStage> rejectStage(@PathVariable String id,
                                                    @PathVariable("stage") StageType stage,
                                                    @RequestBody ProductDtos.DecisionRequest request,
                                                    @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(workflowService.reject(id, stage, request.comment(), principal));
    }

    @RequireRole({Role.ADMIN, Role.CHEF_PROJET})
    @PostMapping("/{id}/stages/{stage}/skip")
    public ResponseEntity<ProjectStage> skipStage(@PathVariable String id,
                                                  @PathVariable("stage") StageType stage,
                                                  @RequestBody ProductDtos.DecisionRequest request,
                                                  @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(workflowService.skipStage(id, stage, request.comment(), principal));
    }

    @RequireRole({Role.ADMIN, Role.CHEF_PROJET, Role.METHODISTE, Role.QUALITICIEN,
            Role.RESPONSABLE_PRODUCTION, Role.CONTROLE_TECHNIQUE})
    @PutMapping("/{id}/stages/{stage}")
    public ResponseEntity<ProjectStage> updateStage(@PathVariable String id,
                                                    @PathVariable("stage") StageType stage,
                                                    @RequestBody ProductDtos.StageUpdateRequest request,
                                                    @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(workflowService.updateStage(id, stage, request.toStage(), principal));
    }

    /** Rebuilds the pipeline from the template - used after a major replanning. */
    @RequireRole({Role.ADMIN, Role.CHEF_PROJET})
    @PostMapping("/{id}/pipeline/reset")
    public ResponseEntity<List<ProjectStage>> resetPipeline(@PathVariable String id,
                                                            @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(workflowService.initializePipeline(productService.entity(id), principal));
    }
}
