package com.cmrt.pfe.services;

import com.cmrt.pfe.models.AuditLog;
import com.cmrt.pfe.models.enums.AuditAction;
import com.cmrt.pfe.repositories.AuditLogRepository;
import com.cmrt.pfe.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/** Writes the append-only traceability trail and reads it back for the history views. */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void record(AuthPrincipal actor, AuditAction action, String entityType,
                       String entityId, String productId, String summary) {
        record(actor, action, entityType, entityId, productId, summary, null, null);
    }

    public void record(AuthPrincipal actor, AuditAction action, String entityType, String entityId,
                       String productId, String summary, String previousValue, String newValue) {
        auditLogRepository.save(AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .productId(productId)
                .action(action)
                .actorId(actor != null ? actor.userId() : null)
                .actorName(actor != null ? actor.name() : "Systeme")
                .summary(summary)
                .previousValue(previousValue)
                .newValue(newValue)
                .build());
    }

    public List<AuditLog> forProduct(String productId) {
        return auditLogRepository.findByProductIdOrderByTimestampDesc(productId);
    }

    public List<AuditLog> forEntity(String entityType, String entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(entityType, entityId);
    }

    public List<AuditLog> recent(int limit) {
        return auditLogRepository.findAllByOrderByTimestampDesc(PageRequest.of(0, limit));
    }
}
