package com.agentmind.document.service;

import com.agentmind.document.model.DocumentSourceType;

/**
 * Immutable result produced by {@link FileUploadValidator}.
 */
public record FileValidationResult(
        String safeFilename,
        DocumentSourceType sourceType,
        String contentType,
        long size
) {
}
