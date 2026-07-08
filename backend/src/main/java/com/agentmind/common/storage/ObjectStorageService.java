package com.agentmind.common.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Storage abstraction used by ingestion services.
 *
 * <p>The application layer depends on this interface instead of a concrete local disk or MinIO implementation.
 * That keeps the current local development adapter replaceable when the project moves to real object storage.</p>
 */
public interface ObjectStorageService {

    StoredObject store(String category, String originalName, InputStream inputStream, long size, String contentType)
            throws IOException;
}
