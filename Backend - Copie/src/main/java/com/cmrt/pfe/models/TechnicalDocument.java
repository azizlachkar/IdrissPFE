package com.cmrt.pfe.models;

import com.cmrt.pfe.models.enums.DocumentStatus;
import com.cmrt.pfe.models.enums.DocumentType;
import com.cmrt.pfe.models.enums.StageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A controlled engineering document. The record is the document's identity; every upload
 * appends an immutable {@link Version} so the revision history is never overwritten.
 * Only one version is APPROVED at a time - approving a new one makes its predecessor
 * OBSOLETE.
 */
@Document(collection = "documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechnicalDocument {

    @Id
    private String id;

    private String name;

    private DocumentType type;

    @Indexed
    private String productId;

    /** The gate this document is a deliverable of. */
    private StageType stageType;

    private String description;

    /** Version label of the currently approved revision, e.g. {@code C}. */
    private String currentVersion;

    @Builder.Default
    private DocumentStatus status = DocumentStatus.DRAFT;

    @Builder.Default
    private List<Version> versions = new ArrayList<>();

    private String ownerId;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    /** The newest revision uploaded, approved or not. */
    public Version latestVersion() {
        return versions == null ? null : versions.stream()
                .max(Comparator.comparing(Version::getUploadedAt))
                .orElse(null);
    }

    public boolean isApproved() {
        return status == DocumentStatus.APPROVED;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Version {
        /** Revision label: A, B, C ... */
        private String version;
        private String fileName;
        /** Path on the server's document store, relative to the configured upload root. */
        private String storedPath;
        private String contentType;
        private Long sizeBytes;
        private String uploadedBy;
        private String uploadedByName;
        @Builder.Default
        private LocalDateTime uploadedAt = LocalDateTime.now();
        /** What changed in this revision. */
        private String changeNote;
        @Builder.Default
        private DocumentStatus status = DocumentStatus.DRAFT;
        private String approvedBy;
        private LocalDateTime approvedAt;
        /** ECR that drove this revision, when the change went through change control. */
        private String engineeringChangeId;
    }
}
