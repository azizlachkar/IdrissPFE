package com.cmrt.pfe.models;

import com.cmrt.pfe.models.enums.DocumentType;
import com.cmrt.pfe.models.enums.StageStatus;
import com.cmrt.pfe.models.enums.StageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One gate of one product's pipeline. Stored in its own collection rather than embedded
 * in {@link Product} so that cross-product views ("everything blocked at BOM validation",
 * "every gate overdue this week") are single indexed queries.
 */
@Document(collection = "project_stages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectStage {

    @Id
    private String id;

    @Indexed
    private String productId;

    private StageType stageType;

    /** Denormalised from {@link StageType#getOrder()} so Mongo can sort without lookups. */
    private int order;

    @Builder.Default
    private StageStatus status = StageStatus.NOT_STARTED;

    /** User accountable for this gate. */
    private String ownerId;

    private LocalDate plannedStart;

    private LocalDate plannedEnd;

    private LocalDateTime actualStart;

    private LocalDateTime actualEnd;

    @Builder.Default
    private int completionPercent = 0;

    /** Document types that must be attached and approved before this gate can close. */
    @Builder.Default
    private List<DocumentType> requiredDeliverables = new ArrayList<>();

    /** Sign-offs gating this stage. */
    @Builder.Default
    private List<Approval> approvals = new ArrayList<>();

    private String notes;

    /** Set by the workflow engine while at least one blocking issue is open on this gate. */
    @Builder.Default
    private boolean blocked = false;

    private String blockReason;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    /** True when the planned end date has passed and the gate has not closed. */
    public boolean isOverdue() {
        return plannedEnd != null
                && status != StageStatus.COMPLETED
                && status != StageStatus.SKIPPED
                && plannedEnd.isBefore(LocalDate.now());
    }

    public boolean isTerminal() {
        return status == StageStatus.COMPLETED || status == StageStatus.SKIPPED;
    }
}
