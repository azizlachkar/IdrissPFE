package com.cmrt.pfe.models;

import com.cmrt.pfe.models.enums.ChangeStatus;
import com.cmrt.pfe.models.enums.ChangeType;
import com.cmrt.pfe.models.enums.Priority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Engineering change request / order (ECR-ECO). Travels DRAFT to SUBMITTED to
 * UNDER_REVIEW, collects the sign-offs required by its impact, then becomes IMPLEMENTED
 * once the affected documents have been revised.
 */
@Document(collection = "engineering_changes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EngineeringChange {

    @Id
    private String id;

    /** Human-readable key such as {@code ECR-2026-007}. */
    @Indexed(unique = true)
    private String reference;

    @Indexed
    private String productId;

    private String title;

    private String description;

    /** Why the change is needed. */
    private String reason;

    private ChangeType type;

    @Builder.Default
    private ChangeStatus status = ChangeStatus.DRAFT;

    @Builder.Default
    private Priority priority = Priority.MEDIUM;

    private String requesterId;

    /** Impact assessment captured at submission. */
    private String impactDescription;

    private Double estimatedCost;

    private Integer estimatedLeadTimeDays;

    @Builder.Default
    private boolean impactsTooling = false;

    @Builder.Default
    private boolean impactsTestMeans = false;

    @Builder.Default
    private boolean requiresCustomerApproval = false;

    @Builder.Default
    private List<Approval> approvals = new ArrayList<>();

    /** Ids of the documents that must be revised for this change. */
    @Builder.Default
    private List<String> affectedDocumentIds = new ArrayList<>();

    private LocalDate effectiveDate;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime submittedAt;

    private LocalDateTime decidedAt;

    private LocalDateTime implementedAt;

    /** Days between submission and final decision, the ECR cycle-time KPI. */
    public Long getCycleTimeDays() {
        if (submittedAt == null || decidedAt == null) return null;
        return Duration.between(submittedAt, decidedAt).toDays();
    }
}
