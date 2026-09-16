package com.cmrt.pfe.models;

import com.cmrt.pfe.models.enums.NotificationType;
import com.cmrt.pfe.models.enums.Severity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    private String id;

    @Indexed
    private String recipientId;

    private NotificationType type;

    private String title;

    private String message;

    /** Front-end route the notification deep-links to, e.g. {@code /products/64f.../pipeline}. */
    private String link;

    @Builder.Default
    private Severity severity = Severity.MINOR;

    @Builder.Default
    private boolean read = false;

    private LocalDateTime readAt;

    /** Who or what triggered it; null for system-generated alerts. */
    private String actorId;

    private String actorName;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
