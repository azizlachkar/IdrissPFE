package com.cmrt.pfe.models;

import com.cmrt.pfe.models.enums.IssueCategory;
import com.cmrt.pfe.models.enums.IssueStatus;
import com.cmrt.pfe.models.enums.Severity;
import com.cmrt.pfe.models.enums.StageType;
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
 * A blockage raised from the shop floor or from engineering. This is the "reclamation"
 * button of the original brief, grown into a full record: category, severity, owner,
 * SLA, root cause and corrective action.
 */
@Document(collection = "issues")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Issue {

    @Id
    private String id;

    /** Human-readable key such as {@code BLK-2026-014}. */
    @Indexed(unique = true)
    private String reference;

    @Indexed
    private String productId;

    /** The gate the blockage was raised against, when applicable. */
    private StageType stageType;

    private String title;

    private IssueCategory category;

    @Builder.Default
    private Severity severity = Severity.MAJOR;

    @Builder.Default
    private IssueStatus status = IssueStatus.OPEN;

    private String description;

    private String reporterId;

    @Indexed
    private String assigneeId;

    /** When true the workflow engine flags the owning stage and product as blocked. */
    @Builder.Default
    private boolean blocksStage = false;

    private String rootCause;

    private String correctiveAction;

    private LocalDate dueDate;

    /** Response time promised for this severity, used to compute SLA breaches. */
    private Integer slaHours;

    @Builder.Default
    private List<Comment> comments = new ArrayList<>();

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime acknowledgedAt;

    private LocalDateTime resolvedAt;

    private LocalDateTime closedAt;

    public boolean isOpen() {
        return status != IssueStatus.CLOSED && status != IssueStatus.RESOLVED && status != IssueStatus.REJECTED;
    }

    /** Hours between creation and resolution, or {@code null} while still open. */
    public Long getResolutionHours() {
        if (createdAt == null || resolvedAt == null) return null;
        return Duration.between(createdAt, resolvedAt).toHours();
    }

    /** True when the promised response time has elapsed without a resolution. */
    public boolean isSlaBreached() {
        if (slaHours == null || createdAt == null) return false;
        LocalDateTime deadline = createdAt.plusHours(slaHours);
        LocalDateTime ref = resolvedAt != null ? resolvedAt : LocalDateTime.now();
        return ref.isAfter(deadline);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Comment {
        private String authorId;
        private String authorName;
        private String message;
        @Builder.Default
        private LocalDateTime createdAt = LocalDateTime.now();
    }
}
