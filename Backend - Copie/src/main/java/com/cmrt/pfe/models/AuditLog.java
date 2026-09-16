package com.cmrt.pfe.models;

import com.cmrt.pfe.models.enums.AuditAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Append-only traceability record. Every state change that matters for an audit -
 * gate approvals, document revisions, ECR decisions, reservations - lands here with
 * the actor, the entity and a human-readable summary of what moved.
 */
@Document(collection = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    private String id;

    /** Simple name of the entity class, e.g. {@code ProjectStage}. */
    @Indexed
    private String entityType;

    @Indexed
    private String entityId;

    private AuditAction action;

    private String actorId;

    private String actorName;

    /** Human-readable summary shown in the traceability timeline. */
    private String summary;

    private String previousValue;

    private String newValue;

    /** Product this event belongs to, so a product's full history is one query. */
    @Indexed
    private String productId;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
