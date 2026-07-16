package com.agentmind.document.model;

import java.time.OffsetDateTime;
import java.util.List;

/** 文档元数据持久化快照，原始正文和向量不存入该模型。 */
public record DocumentMetadata(
        Long id,
        Long ownerUserId,
        Long workspaceId,
        String title,
        DocumentSourceType sourceType,
        String sourceUri,
        String originalFilename,
        String storageKey,
        String contentType,
        long contentSize,
        String contentHash,
        List<String> tags,
        IngestionStatus ingestionStatus,
        int chunkCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public DocumentMetadata {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
