package com.agentmind.document.service;

import com.agentmind.document.model.DocumentSourceType;

/**
 * 文件上传校验后的不可变结果。
 */
public record FileValidationResult(
        String safeFilename,
        DocumentSourceType sourceType,
        String contentType,
        long size
) {
}
