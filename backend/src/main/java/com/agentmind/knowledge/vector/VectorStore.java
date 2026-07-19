package com.agentmind.knowledge.vector;

import com.agentmind.knowledge.model.KnowledgeVector;
import com.agentmind.knowledge.model.VectorSearchResult;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 向量持久化与检索端口。
 */
public interface VectorStore {

    void replaceDocumentVectors(Long workspaceId, Long documentId, Collection<KnowledgeVector> vectors);

    void deleteDocumentVectors(Long workspaceId, Long documentId);

    List<VectorSearchResult> search(Long workspaceId, float[] queryEmbedding, int topK);

    /** 按用户显式选择的文档范围检索；空集合表示整个知识空间。 */
    default List<VectorSearchResult> search(
            Long workspaceId,
            float[] queryEmbedding,
            int topK,
            Set<Long> documentIds
    ) {
        if (documentIds == null || documentIds.isEmpty()) {
            return search(workspaceId, queryEmbedding, topK);
        }
        return search(workspaceId, queryEmbedding, Math.max(topK, 200)).stream()
                .filter(result -> documentIds.contains(result.documentId()))
                .limit(topK)
                .toList();
    }
}
