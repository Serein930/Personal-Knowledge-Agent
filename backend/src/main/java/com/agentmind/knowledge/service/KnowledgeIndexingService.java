package com.agentmind.knowledge.service;

import com.agentmind.document.chunk.DocumentChunk;
import com.agentmind.knowledge.model.KnowledgeVector;
import com.agentmind.knowledge.vector.EmbeddingClient;
import com.agentmind.knowledge.vector.VectorStore;
import com.agentmind.knowledge.keyword.KeywordIndex;
import com.agentmind.knowledge.outbox.service.DirectKnowledgeIndexTransactionExecutor;
import com.agentmind.knowledge.outbox.service.ImmediateKnowledgeIndexChangePublisher;
import com.agentmind.knowledge.outbox.service.KnowledgeIndexChangePublisher;
import com.agentmind.knowledge.outbox.service.KnowledgeIndexTransactionExecutor;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 为文档片段生成并保存向量。
 */
@Service
public class KnowledgeIndexingService {

    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;
    private final KnowledgeIndexChangePublisher indexChangePublisher;
    private final KnowledgeIndexTransactionExecutor transactionExecutor;

    @Autowired
    public KnowledgeIndexingService(
            EmbeddingClient embeddingClient,
            VectorStore vectorStore,
            KnowledgeIndexChangePublisher indexChangePublisher,
            KnowledgeIndexTransactionExecutor transactionExecutor
    ) {
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
        this.indexChangePublisher = indexChangePublisher;
        this.transactionExecutor = transactionExecutor;
    }

    /** 为不启动 Spring 容器的单元测试保留轻量构造方式。 */
    public KnowledgeIndexingService(
            EmbeddingClient embeddingClient,
            VectorStore vectorStore,
            KeywordIndex keywordIndex
    ) {
        this(embeddingClient, vectorStore, new ImmediateKnowledgeIndexChangePublisher(keywordIndex),
                new DirectKnowledgeIndexTransactionExecutor());
    }

    public void indexChunks(Long workspaceId, Long documentId, List<DocumentChunk> chunks) {
        List<KnowledgeVector> vectors = chunks.stream()
                .map(chunk -> toVector(workspaceId, chunk))
                .toList();
        transactionExecutor.execute(() -> {
            vectorStore.replaceDocumentVectors(workspaceId, documentId, vectors);
            indexChangePublisher.publishUpsert(workspaceId, documentId, chunks);
        });
    }

    public void deleteDocumentIndex(Long workspaceId, Long documentId) {
        transactionExecutor.execute(() -> {
            vectorStore.deleteDocumentVectors(workspaceId, documentId);
            indexChangePublisher.publishDelete(workspaceId, documentId);
        });
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
