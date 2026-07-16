package com.agentmind.knowledge.outbox.service;

import com.agentmind.document.chunk.DocumentChunk;
import com.agentmind.knowledge.outbox.config.KnowledgeIndexOutboxProperties;
import com.agentmind.knowledge.outbox.model.KnowledgeIndexRebuildResult;
import com.agentmind.knowledge.outbox.repository.KnowledgeVectorSnapshotRepository;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 将 pgvector 中的权威片段快照重新投递到 OpenSearch 索引队列。 */
@Service
@ConditionalOnProperty(prefix = "agentmind.knowledge-index.outbox", name = "enabled", havingValue = "true")
public class KnowledgeIndexRebuildService {

    private final KnowledgeVectorSnapshotRepository snapshotRepository;
    private final KnowledgeIndexChangePublisher publisher;
    private final KnowledgeIndexOutboxProperties properties;

    public KnowledgeIndexRebuildService(
            KnowledgeVectorSnapshotRepository snapshotRepository,
            KnowledgeIndexChangePublisher publisher,
            KnowledgeIndexOutboxProperties properties
    ) {
        this.snapshotRepository = snapshotRepository;
        this.publisher = publisher;
        this.properties = properties;
    }

    public KnowledgeIndexRebuildResult rebuild(Long workspaceId) {
        long documentCount = 0;
        long chunkCount = 0;
        long eventCount = 0;
        long cursor = 0L;
        while (true) {
            List<Long> documentIds = snapshotRepository.findDocumentIds(
                    workspaceId, cursor, properties.getRebuildDocumentBatchSize());
            if (documentIds.isEmpty()) {
                break;
            }
            for (Long documentId : documentIds) {
                List<DocumentChunk> chunks = snapshotRepository.findChunks(workspaceId, documentId);
                publisher.publishUpsert(workspaceId, documentId, chunks);
                documentCount++;
                chunkCount += chunks.size();
                eventCount++;
            }
            cursor = documentIds.getLast();
        }
        return new KnowledgeIndexRebuildResult(workspaceId, documentCount, chunkCount, eventCount);
    }
}
