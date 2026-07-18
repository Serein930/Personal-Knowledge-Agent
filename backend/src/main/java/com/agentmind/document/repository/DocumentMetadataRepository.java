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

    void markSucceeded(Long documentId, String storageKey, String contentType, long contentSize,
            String contentHash, int chunkCount);

    /** 兼容不关心内容哈希的仓储测试和旧调用方。 */
    default void markSucceeded(Long documentId, String storageKey, String contentType,
            long contentSize, int chunkCount) {
        markSucceeded(documentId, storageKey, contentType, contentSize, null, chunkCount);
    }

    void markFailed(Long documentId);

    Optional<DocumentMetadata> findByWorkspaceIdAndId(Long workspaceId, Long documentId);

    Optional<DocumentMetadata> findById(Long documentId);

    Optional<DocumentMetadata> findLatestByWorkspaceIdAndSourceUri(Long workspaceId, String sourceUri);

    List<DocumentMetadata> findAllByWorkspaceId(Long workspaceId);
}
