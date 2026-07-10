package com.agentmind.knowledge.service;

import com.agentmind.document.chunk.DocumentChunk;
import com.agentmind.knowledge.model.KnowledgeVector;
import com.agentmind.knowledge.vector.EmbeddingClient;
import com.agentmind.knowledge.vector.VectorStore;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 为文档片段生成并保存向量。
 */
@Service
public class KnowledgeIndexingService {

    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;

    public KnowledgeIndexingService(EmbeddingClient embeddingClient, VectorStore vectorStore) {
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
    }

    public void indexChunks(Long workspaceId, Long documentId, List<DocumentChunk> chunks) {
        List<KnowledgeVector> vectors = chunks.stream()
                .map(chunk -> toVector(workspaceId, chunk))
                .toList();
        vectorStore.replaceDocumentVectors(workspaceId, documentId, vectors);
    }

    public void deleteDocumentIndex(Long workspaceId, Long documentId) {
        vectorStore.deleteDocumentVectors(workspaceId, documentId);
    }

    private KnowledgeVector toVector(Long workspaceId, DocumentChunk chunk) {
        return new KnowledgeVector(
                workspaceId + ":" + chunk.id(),
                workspaceId,
                chunk.documentId(),
                chunk.id(),
                chunk.sequence(),
                chunk.headingPath(),
                chunk.content(),
                embeddingClient.embed(chunk.content()),
                OffsetDateTime.now()
        );
    }
}
