package com.agentmind.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Local disk implementation of {@link ObjectStorageService}.
 *
 * <p>This adapter is for Stage 4 development and API integration. It stores uploaded files and fetched HTML
 * snapshots under `.agentmind-storage`, which is ignored by Git. A future MinIO adapter can implement the same
 * interface without changing the document ingestion API.</p>
 */
@Service
public class LocalObjectStorageService implements ObjectStorageService {

    private final Path rootPath;

    public LocalObjectStorageService(@Value("${agentmind.storage.local-root:.agentmind-storage}") String localRoot) {
        this.rootPath = Path.of(localRoot).toAbsolutePath().normalize();
    }

    @Override
    public StoredObject store(String category, String originalName, InputStream inputStream, long size, String contentType)
            throws IOException {
        String safeCategory = sanitizePathSegment(category);
        String safeOriginalName = sanitizeFilename(originalName);
        String objectName = UUID.randomUUID() + "-" + safeOriginalName;
        Path targetDirectory = rootPath.resolve(safeCategory).normalize();
        Path targetPath = targetDirectory.resolve(objectName).normalize();

        if (!targetPath.startsWith(rootPath)) {
            throw new IOException("Invalid storage path");
        }

        Files.createDirectories(targetDirectory);
        Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);

        String storageKey = safeCategory + "/" + objectName;
        return new StoredObject(storageKey, targetPath, safeOriginalName, contentType, size);
    }

    private String sanitizePathSegment(String value) {
        if (!StringUtils.hasText(value)) {
            return "default";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "-");
    }

    private String sanitizeFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "unnamed";
        }
        String normalized = Path.of(filename).getFileName().toString();
        return normalized.replaceAll("[\\\\/:*?\"<>|]", "-");
    }
}
