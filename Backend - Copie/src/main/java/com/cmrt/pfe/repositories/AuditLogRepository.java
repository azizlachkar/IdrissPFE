package com.cmrt.pfe.repositories;

import com.cmrt.pfe.models.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
    List<AuditLog> findByProductIdOrderByTimestampDesc(String productId);
    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(String entityType, String entityId);
    List<AuditLog> findByActorIdOrderByTimestampDesc(String actorId);
    List<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);
}
