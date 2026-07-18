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
import java.util.stream.IntStream;
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
        indexChunks(workspaceId, documentId, chunks, null);
    }

    /** 使用知识空间偏好指定的向量模型建立索引。 */
    public void indexChunks(Long workspaceId, Long documentId, List<DocumentChunk> chunks, String embeddingModel) {
        List<String> texts = chunks.stream().map(DocumentChunk::content).toList();
        List<float[]> embeddings = org.springframework.util.StringUtils.hasText(embeddingModel)
                ? embeddingClient.embedAll(texts, embeddingModel)
                : embeddingClient.embedAll(texts);
        if (embeddings.size() != chunks.size()) {
            throw new IllegalStateException("向量模型返回数量与文档片段数量不一致");
        }
        OffsetDateTime indexedAt = OffsetDateTime.now();
        List<KnowledgeVector> vectors = IntStream.range(0, chunks.size())
                .mapToObj(index -> toVector(workspaceId, chunks.get(index), embeddings.get(index), indexedAt))
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

    private KnowledgeVector toVector(
            Long workspaceId,
            DocumentChunk chunk,
            float[] embedding,
            OffsetDateTime indexedAt
    ) {
        return new KnowledgeVector(
                workspaceId + ":" + chunk.id(),
                workspaceId,
                chunk.documentId(),
                chunk.id(),
                chunk.sequence(),
                chunk.headingPath(),
                chunk.content(),
                embedding,
                indexedAt
        );
    }
}
