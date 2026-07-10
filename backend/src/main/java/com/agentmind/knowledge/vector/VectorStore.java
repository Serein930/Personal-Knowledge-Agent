package com.agentmind.knowledge.vector;

import com.agentmind.knowledge.model.KnowledgeVector;
import com.agentmind.knowledge.model.VectorSearchResult;
import java.util.Collection;
import java.util.List;

/**
 * 向量持久化与检索端口。
 */
public interface VectorStore {

    void replaceDocumentVectors(Long workspaceId, Long documentId, Collection<KnowledgeVector> vectors);

    void deleteDocumentVectors(Long workspaceId, Long documentId);

    List<VectorSearchResult> search(Long workspaceId, float[] queryEmbedding, int topK);
}
