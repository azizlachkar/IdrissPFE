package com.cmrt.pfe.services;

import com.cmrt.pfe.exceptions.ApiException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Stores uploaded document revisions on disk. Files are written under a per-product
 * folder with a generated name, so two revisions sharing a filename never collide and
 * the original name stays a display detail held in the database.
 */
@Service
@Slf4j
public class StorageService {

    private final Path root;

    public StorageService(@Value("${app.storage.upload-dir}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(root);
            log.info("Document store: {}", root);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de creer le repertoire de stockage : " + root, e);
        }
    }

    /** Saves the upload and returns the path to record on the version, relative to the store root. */
    public String store(MultipartFile file, String productId) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Le fichier est vide");
        }
        try {
            Path folder = root.resolve(productId == null ? "general" : productId);
            Files.createDirectories(folder);

            String storedName = UUID.randomUUID() + extensionOf(file.getOriginalFilename());
            Path target = folder.resolve(storedName);

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return root.relativize(target).toString().replace('\\', '/');
        } catch (IOException e) {
            throw new IllegalStateException("Echec de l'enregistrement du fichier", e);
        }
    }

    public Resource load(String storedPath) {
        try {
            Path file = resolve(storedPath);
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw ApiException.notFound("Fichier");
            }
            return resource;
        } catch (java.net.MalformedURLException e) {
            throw ApiException.notFound("Fichier");
        }
    }

    public void delete(String storedPath) {
        try {
            Files.deleteIfExists(resolve(storedPath));
        } catch (IOException e) {
            log.warn("Impossible de supprimer {} : {}", storedPath, e.getMessage());
        }
    }

    /** Resolves a stored path, refusing anything that escapes the store root. */
    private Path resolve(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            throw ApiException.notFound("Fichier");
        }
        Path file = root.resolve(storedPath).normalize();
        if (!file.startsWith(root)) {
            throw ApiException.forbidden("Chemin de fichier invalide");
        }
        return file;
    }

    private String extensionOf(String originalName) {
        if (originalName == null) return "";
        int dot = originalName.lastIndexOf('.');
        return dot >= 0 ? originalName.substring(dot) : "";
    }
}
