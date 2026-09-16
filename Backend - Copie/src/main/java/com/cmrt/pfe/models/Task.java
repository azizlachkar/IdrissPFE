package com.cmrt.pfe.models;

import com.cmrt.pfe.models.enums.Priority;
import com.cmrt.pfe.models.enums.StageType;
import com.cmrt.pfe.models.enums.TaskStatus;
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

@Document(collection = "tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    @Id
    private String id;

    private String title;

    private String description;

    @Indexed
    private String productId;

    /** Optional link to the pipeline gate this task belongs to. */
    private StageType stageType;

    @Indexed
    private String assigneeId;

    private String reporterId;

    @Builder.Default
    private TaskStatus status = TaskStatus.TODO;

    @Builder.Default
    private Priority priority = Priority.MEDIUM;

    private LocalDate dueDate;

    private Double estimatedHours;

    private Double spentHours;

    @Builder.Default
    private List<ChecklistItem> checklist = new ArrayList<>();

    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    private LocalDateTime completedAt;

    public boolean isOverdue() {
        return dueDate != null
                && status != TaskStatus.DONE
                && status != TaskStatus.CANCELLED
                && dueDate.isBefore(LocalDate.now());
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChecklistItem {
        private String label;
        @Builder.Default
        private boolean done = false;
    }
}
