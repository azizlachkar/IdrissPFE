package com.cmrt.pfe.services;

import com.cmrt.pfe.exceptions.ApiException;
import com.cmrt.pfe.models.TestResource;
import com.cmrt.pfe.models.enums.AuditAction;
import com.cmrt.pfe.models.enums.ResourceStatus;
import com.cmrt.pfe.repositories.ResourceRepository;
import com.cmrt.pfe.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Manages the technical-control assets: test benches, build boards, interfaces, tooling. */
@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final AuditService auditService;

    public List<TestResource> findAll() {
        return resourceRepository.findAll();
    }

    public List<TestResource> findAvailable() {
        return resourceRepository.findByStatus(ResourceStatus.AVAILABLE);
    }

    public TestResource findById(String id) {
        return resourceRepository.findById(id).orElseThrow(() -> ApiException.notFound("Moyen de test"));
    }

    public TestResource create(TestResource request, AuthPrincipal actor) {
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw ApiException.badRequest("Le code du moyen est obligatoire");
        }
        String code = request.getCode().trim().toUpperCase();
        if (resourceRepository.existsByCodeIgnoreCase(code)) {
            throw ApiException.badRequest("Un moyen porte deja le code " + code);
        }

        TestResource resource = TestResource.builder()
                .code(code)
                .nom(request.getNom())
                .type(request.getType())
                .status(request.getStatus() != null ? request.getStatus() : ResourceStatus.AVAILABLE)
                .location(request.getLocation())
                .description(request.getDescription())
                .ownerId(request.getOwnerId())
                .compatibleProductIds(request.getCompatibleProductIds() != null
                        ? request.getCompatibleProductIds() : new ArrayList<>())
                .lastMaintenanceDate(request.getLastMaintenanceDate())
                .nextMaintenanceDate(request.getNextMaintenanceDate())
                .requiresCalibration(request.isRequiresCalibration())
                .calibrationExpiry(request.getCalibrationExpiry())
                .build();

        TestResource saved = resourceRepository.save(resource);
        auditService.record(actor, AuditAction.CREATE, "TestResource", saved.getId(), null,
                "Creation du moyen " + saved.getCode());
        return saved;
    }

    public TestResource update(String id, TestResource changes, AuthPrincipal actor) {
        TestResource resource = findById(id);
        if (changes.getNom() != null) resource.setNom(changes.getNom());
        if (changes.getType() != null) resource.setType(changes.getType());
        if (changes.getLocation() != null) resource.setLocation(changes.getLocation());
        if (changes.getDescription() != null) resource.setDescription(changes.getDescription());
        if (changes.getOwnerId() != null) resource.setOwnerId(changes.getOwnerId());
        if (changes.getCompatibleProductIds() != null) resource.setCompatibleProductIds(changes.getCompatibleProductIds());
        if (changes.getLastMaintenanceDate() != null) resource.setLastMaintenanceDate(changes.getLastMaintenanceDate());
        if (changes.getNextMaintenanceDate() != null) resource.setNextMaintenanceDate(changes.getNextMaintenanceDate());
        if (changes.getCalibrationExpiry() != null) resource.setCalibrationExpiry(changes.getCalibrationExpiry());
        resource.setRequiresCalibration(changes.isRequiresCalibration());
        resource.setUpdatedAt(LocalDateTime.now());

        TestResource saved = resourceRepository.save(resource);
        auditService.record(actor, AuditAction.UPDATE, "TestResource", id, null,
                "Mise a jour du moyen " + resource.getCode());
        return saved;
    }

    public TestResource changeStatus(String id, ResourceStatus status, AuthPrincipal actor) {
        TestResource resource = findById(id);
        ResourceStatus previous = resource.getStatus();
        resource.setStatus(status);
        resource.setUpdatedAt(LocalDateTime.now());
        TestResource saved = resourceRepository.save(resource);
        auditService.record(actor, AuditAction.STATUS_CHANGE, "TestResource", id, null,
                "Moyen " + resource.getCode() + " : " + previous + " -> " + status,
                previous != null ? previous.name() : null, status.name());
        return saved;
    }

    public void delete(String id, AuthPrincipal actor) {
        TestResource resource = findById(id);
        resourceRepository.deleteById(id);
        auditService.record(actor, AuditAction.DELETE, "TestResource", id, null,
                "Suppression du moyen " + resource.getCode());
    }
}
