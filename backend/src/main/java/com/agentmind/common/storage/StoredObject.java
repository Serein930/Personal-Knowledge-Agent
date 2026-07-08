package com.agentmind.common.storage;

import java.nio.file.Path;

/**
 * Metadata returned after an object has been stored.
 *
 * <p>The absolute path is useful for local development only. Higher layers should use storageKey as the stable
 * reference so the same contract can work with MinIO or another object storage service later.</p>
 */
public record StoredObject(
        String storageKey,
        Path absolutePath,
        String originalName,
        String contentType,
        long size
) {
}
