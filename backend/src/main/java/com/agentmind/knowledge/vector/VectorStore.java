package com.agentmind.knowledge.vector;

import com.agentmind.knowledge.model.KnowledgeVector;
import com.agentmind.knowledge.model.VectorSearchResult;
import java.util.Collection;
import java.util.List;

/**
 * Port for vector persistence and retrieval.
 */
public interface VectorStore {

    void replaceDocumentVectors(Long workspaceId, Long documentId, Collection<KnowledgeVector> vectors);

    void deleteDocumentVectors(Long workspaceId, Long documentId);

    List<VectorSearchResult> search(Long workspaceId, float[] queryEmbedding, int topK);
}
