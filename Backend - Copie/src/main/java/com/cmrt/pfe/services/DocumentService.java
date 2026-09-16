package com.cmrt.pfe.services;

import com.cmrt.pfe.exceptions.ApiException;
import com.cmrt.pfe.models.Product;
import com.cmrt.pfe.models.TechnicalDocument;
import com.cmrt.pfe.models.enums.AuditAction;
import com.cmrt.pfe.models.enums.DocumentStatus;
import com.cmrt.pfe.models.enums.DocumentType;
import com.cmrt.pfe.models.enums.NotificationType;
import com.cmrt.pfe.models.enums.Severity;
import com.cmrt.pfe.models.enums.StageType;
import com.cmrt.pfe.repositories.DocumentRepository;
import com.cmrt.pfe.repositories.ProductRepository;
import com.cmrt.pfe.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Controlled document management with revision history.
 * <p>
 * Uploading against an existing document appends revision B, C, D ... rather than
 * replacing the file. Approving a revision makes every earlier one obsolete, which is
 * what {@link WorkflowService} checks when it decides whether a gate's deliverables are
 * satisfied.
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final ProductRepository productRepository;
    private final StorageService storageService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public List<TechnicalDocument> findByProduct(String productId) {
        return documentRepository.findByProductId(productId);
    }

    public List<TechnicalDocument> findAll() {
        return documentRepository.findAll();
    }

    public TechnicalDocument findById(String id) {
        return documentRepository.findById(id).orElseThrow(() -> ApiException.notFound("Document"));
    }

    /**
     * Uploads a revision. Passing {@code documentId} adds a revision to an existing
     * document; omitting it creates the document at revision A.
     */
    public TechnicalDocument upload(MultipartFile file, String documentId, String productId,
                                    DocumentType type, StageType stageType, String name,
                                    String changeNote, String engineeringChangeId, AuthPrincipal actor) {
        if (productId == null || !productRepository.existsById(productId)) {
            throw ApiException.notFound("Produit");
        }

        TechnicalDocument document;
        if (documentId != null && !documentId.isBlank()) {
            document = findById(documentId);
        } else {
            document = TechnicalDocument.builder()
                    .productId(productId)
                    .type(type != null ? type : DocumentType.OTHER)
                    .stageType(stageType)
                    .name(name != null && !name.isBlank() ? name.trim() : file.getOriginalFilename())
                    .ownerId(actor != null ? actor.userId() : null)
                    .versions(new ArrayList<>())
                    .status(DocumentStatus.DRAFT)
                    .build();
        }

        String storedPath = storageService.store(file, productId);
        String versionLabel = nextVersionLabel(document);

        TechnicalDocument.Version version = TechnicalDocument.Version.builder()
                .version(versionLabel)
                .fileName(file.getOriginalFilename())
                .storedPath(storedPath)
                .contentType(file.getContentType())
                .sizeBytes(file.getSize())
                .uploadedBy(actor != null ? actor.userId() : null)
                .uploadedByName(actor != null ? actor.name() : "Systeme")
                .changeNote(changeNote)
                .engineeringChangeId(engineeringChangeId)
                .status(DocumentStatus.IN_REVIEW)
                .build();

        if (document.getVersions() == null) {
            document.setVersions(new ArrayList<>());
        }
        document.getVersions().add(version);
        // A new revision puts the document back under review until it is approved.
        document.setStatus(DocumentStatus.IN_REVIEW);
        document.setUpdatedAt(LocalDateTime.now());
        if (stageType != null) document.setStageType(stageType);
        if (type != null) document.setType(type);

        TechnicalDocument saved = documentRepository.save(document);
        Product product = productRepository.findById(productId).orElse(null);

        auditService.record(actor, AuditAction.UPLOAD, "TechnicalDocument", saved.getId(), productId,
                "Revision " + versionLabel + " de " + saved.getName() + " deposee");

        if (product != null) {
            notificationService.notifyAll(
                    // Stream.of, not List.of: these owners may be unassigned.
                    Stream.of(product.getQualiticienId(), product.getChefProjetId())
                            .filter(Objects::nonNull).toList(),
                    NotificationType.DOCUMENT_UPLOADED, Severity.MINOR,
                    "Document a valider : " + saved.getName(),
                    "Revision " + versionLabel + " deposee pour " + product.getReference()
                            + (changeNote != null ? " - " + changeNote : ""),
                    "/products/" + productId, actor);
        }
        return saved;
    }

    /** A, B, C ... then AA if a document ever exceeds 26 revisions. */
    private String nextVersionLabel(TechnicalDocument document) {
        int count = document.getVersions() == null ? 0 : document.getVersions().size();
        if (count < 26) {
            return String.valueOf((char) ('A' + count));
        }
        return "A" + (char) ('A' + (count - 26) % 26);
    }

    /**
     * Approves a revision. The document takes that revision as its current one and every
     * earlier revision becomes obsolete - only one approved revision exists at a time.
     */
    public TechnicalDocument approveVersion(String documentId, String versionLabel, AuthPrincipal actor) {
        TechnicalDocument document = findById(documentId);
        TechnicalDocument.Version target = document.getVersions().stream()
                .filter(v -> v.getVersion().equals(versionLabel))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Revision " + versionLabel));

        if (target.getStatus() == DocumentStatus.APPROVED) {
            throw ApiException.conflict("Cette revision est deja approuvee");
        }

        document.getVersions().stream()
                .filter(v -> v.getStatus() == DocumentStatus.APPROVED)
                .forEach(v -> v.setStatus(DocumentStatus.OBSOLETE));

        target.setStatus(DocumentStatus.APPROVED);
        target.setApprovedBy(actor != null ? actor.userId() : null);
        target.setApprovedAt(LocalDateTime.now());

        document.setCurrentVersion(versionLabel);
        document.setStatus(DocumentStatus.APPROVED);
        document.setUpdatedAt(LocalDateTime.now());

        TechnicalDocument saved = documentRepository.save(document);
        auditService.record(actor, AuditAction.APPROVE, "TechnicalDocument", documentId, document.getProductId(),
                "Revision " + versionLabel + " de " + document.getName() + " approuvee");
        notificationService.notify(target.getUploadedBy(), NotificationType.DOCUMENT_APPROVED, Severity.MINOR,
                "Document approuve : " + document.getName(),
                "La revision " + versionLabel + " a ete approuvee.",
                "/products/" + document.getProductId(), actor);
        return saved;
    }

    public TechnicalDocument rejectVersion(String documentId, String versionLabel, String reason, AuthPrincipal actor) {
        TechnicalDocument document = findById(documentId);
        TechnicalDocument.Version target = document.getVersions().stream()
                .filter(v -> v.getVersion().equals(versionLabel))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Revision " + versionLabel));

        target.setStatus(DocumentStatus.DRAFT);
        target.setChangeNote((target.getChangeNote() == null ? "" : target.getChangeNote() + " | ")
                + "Refus : " + reason);
        // The document keeps whatever revision was approved before, if any.
        document.setStatus(document.getCurrentVersion() != null ? DocumentStatus.APPROVED : DocumentStatus.DRAFT);
        document.setUpdatedAt(LocalDateTime.now());

        TechnicalDocument saved = documentRepository.save(document);
        auditService.record(actor, AuditAction.REJECT, "TechnicalDocument", documentId, document.getProductId(),
                "Revision " + versionLabel + " refusee : " + reason);
        notificationService.notify(target.getUploadedBy(), NotificationType.GENERIC, Severity.MAJOR,
                "Document refuse : " + document.getName(), "Motif : " + reason,
                "/products/" + document.getProductId(), actor);
        return saved;
    }

    /** Streams a revision's file back to the browser. */
    public Resource download(String documentId, String versionLabel) {
        TechnicalDocument document = findById(documentId);
        TechnicalDocument.Version version = versionLabel == null
                ? currentOrLatest(document)
                : document.getVersions().stream()
                        .filter(v -> v.getVersion().equals(versionLabel))
                        .findFirst()
                        .orElseThrow(() -> ApiException.notFound("Revision " + versionLabel));
        return storageService.load(version.getStoredPath());
    }

    public TechnicalDocument.Version resolveVersion(String documentId, String versionLabel) {
        TechnicalDocument document = findById(documentId);
        return versionLabel == null
                ? currentOrLatest(document)
                : document.getVersions().stream()
                        .filter(v -> v.getVersion().equals(versionLabel))
                        .findFirst()
                        .orElseThrow(() -> ApiException.notFound("Revision " + versionLabel));
    }

    private TechnicalDocument.Version currentOrLatest(TechnicalDocument document) {
        if (document.getCurrentVersion() != null) {
            return document.getVersions().stream()
                    .filter(v -> v.getVersion().equals(document.getCurrentVersion()))
                    .findFirst()
                    .orElseGet(document::latestVersion);
        }
        TechnicalDocument.Version latest = document.latestVersion();
        if (latest == null) {
            throw ApiException.notFound("Revision");
        }
        return latest;
    }

    public void delete(String documentId, AuthPrincipal actor) {
        TechnicalDocument document = findById(documentId);
        if (document.getVersions() != null) {
            document.getVersions().forEach(v -> storageService.delete(v.getStoredPath()));
        }
        documentRepository.deleteById(documentId);
        auditService.record(actor, AuditAction.DELETE, "TechnicalDocument", documentId, document.getProductId(),
                "Suppression du document " + document.getName());
    }
}
