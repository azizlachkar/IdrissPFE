package com.cmrt.pfe.controllers;

import com.cmrt.pfe.models.TechnicalDocument;
import com.cmrt.pfe.models.enums.DocumentType;
import com.cmrt.pfe.models.enums.Role;
import com.cmrt.pfe.models.enums.StageType;
import com.cmrt.pfe.security.AuthPrincipal;
import com.cmrt.pfe.security.CurrentUser;
import com.cmrt.pfe.security.RequireRole;
import com.cmrt.pfe.services.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** Controlled documents and their revision history. */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping
    public ResponseEntity<List<TechnicalDocument>> all(@RequestParam(required = false) String productId) {
        return ResponseEntity.ok(productId == null
                ? documentService.findAll()
                : documentService.findByProduct(productId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TechnicalDocument> byId(@PathVariable String id) {
        return ResponseEntity.ok(documentService.findById(id));
    }

    /**
     * Uploads a revision. Omit {@code documentId} to create the document at revision A;
     * pass it to append the next revision to an existing one.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TechnicalDocument> upload(@RequestPart("file") MultipartFile file,
                                                    @RequestParam(required = false) String documentId,
                                                    @RequestParam String productId,
                                                    @RequestParam(required = false) DocumentType type,
                                                    @RequestParam(required = false) StageType stageType,
                                                    @RequestParam(required = false) String name,
                                                    @RequestParam(required = false) String changeNote,
                                                    @RequestParam(required = false) String engineeringChangeId,
                                                    @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(documentService.upload(file, documentId, productId, type, stageType,
                name, changeNote, engineeringChangeId, principal));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable String id,
                                             @RequestParam(required = false) String version) {
        Resource resource = documentService.download(id, version);
        TechnicalDocument.Version meta = documentService.resolveVersion(id, version);
        return ResponseEntity.ok()
                .contentType(meta.getContentType() != null
                        ? MediaType.parseMediaType(meta.getContentType())
                        : MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + meta.getFileName() + "\"")
                .body(resource);
    }

    @RequireRole({Role.ADMIN, Role.QUALITICIEN, Role.CHEF_PROJET, Role.METHODISTE, Role.CONTROLE_TECHNIQUE})
    @PostMapping("/{id}/versions/{version}/approve")
    public ResponseEntity<TechnicalDocument> approve(@PathVariable String id, @PathVariable String version,
                                                     @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(documentService.approveVersion(id, version, principal));
    }

    @RequireRole({Role.ADMIN, Role.QUALITICIEN, Role.CHEF_PROJET, Role.METHODISTE, Role.CONTROLE_TECHNIQUE})
    @PostMapping("/{id}/versions/{version}/reject")
    public ResponseEntity<TechnicalDocument> reject(@PathVariable String id, @PathVariable String version,
                                                    @RequestBody Map<String, String> body,
                                                    @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.ok(documentService.rejectVersion(id, version, body.get("comment"), principal));
    }

    @RequireRole({Role.ADMIN, Role.CHEF_PROJET})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, @CurrentUser AuthPrincipal principal) {
        documentService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }
}
