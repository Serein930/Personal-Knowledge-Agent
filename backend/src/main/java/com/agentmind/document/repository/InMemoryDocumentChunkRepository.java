package com.agentmind.document.repository;

import com.agentmind.document.chunk.DocumentChunk;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** 零依赖开发模式使用的文档片段仓储。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.core.persistence", name = "store", havingValue = "memory", matchIfMissing = true)
public class InMemoryDocumentChunkRepository implements DocumentChunkRepository {

    private final Map<Long, List<DocumentChunk>> chunks = new ConcurrentHashMap<>();

    @Override
    public void replaceDocumentChunks(Long ownerUserId, Long workspaceId, Long documentId,
            List<DocumentChunk> documentChunks) {
        chunks.put(documentId, List.copyOf(documentChunks));
    }

    @Override
    public List<DocumentChunk> findAllByDocumentId(Long documentId) {
        return chunks.getOrDefault(documentId, List.of());
    }

    @Override
    public void deleteAllByDocumentId(Long documentId) {
        chunks.remove(documentId);
    }
}
