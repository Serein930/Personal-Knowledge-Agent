package com.agentmind.document.repository;

import com.agentmind.document.model.DocumentMetadata;
import com.agentmind.document.model.DocumentSourceType;
import java.util.List;
import java.util.Optional;

/** 文档元数据持久化端口。 */
public interface DocumentMetadataRepository {

    DocumentMetadata create(
            Long ownerUserId,
            Long workspaceId,
            String title,
            DocumentSourceType sourceType,
            String sourceUri,
            String originalFilename,
            List<String> tags
    );

    void markSucceeded(Long documentId, String storageKey, String contentType, long contentSize, int chunkCount);

    void markFailed(Long documentId);

    Optional<DocumentMetadata> findByWorkspaceIdAndId(Long workspaceId, Long documentId);

    Optional<DocumentMetadata> findById(Long documentId);

    List<DocumentMetadata> findAllByWorkspaceId(Long workspaceId);
}
