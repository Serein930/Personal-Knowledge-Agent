package com.agentmind.knowledge.outbox.service;

import com.agentmind.document.chunk.DocumentChunk;
import com.agentmind.knowledge.outbox.model.KnowledgeIndexOutboxOperation;
import com.agentmind.knowledge.outbox.model.KnowledgeIndexOutboxPayload;
import com.agentmind.knowledge.outbox.repository.KnowledgeIndexOutboxRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 将索引变更可靠写入 PostgreSQL Outbox 的生产发布器。 */
@Component
@ConditionalOnProperty(prefix = "agentmind.knowledge-index.outbox", name = "enabled", havingValue = "true")
public class OutboxKnowledgeIndexChangePublisher implements KnowledgeIndexChangePublisher {

    private final KnowledgeIndexOutboxRepository repository;

    public OutboxKnowledgeIndexChangePublisher(KnowledgeIndexOutboxRepository repository) {
        this.repository = repository;
    }

    @Override
    public void publishUpsert(Long workspaceId, Long documentId, List<DocumentChunk> chunks) {
        KnowledgeIndexOutboxPayload payload = new KnowledgeIndexOutboxPayload(chunks);
        enqueue(workspaceId, documentId, KnowledgeIndexOutboxOperation.UPSERT, payload);
    }

    @Override
    public void publishDelete(Long workspaceId, Long documentId) {
        enqueue(workspaceId, documentId, KnowledgeIndexOutboxOperation.DELETE,
                new KnowledgeIndexOutboxPayload(List.of()));
    }

    private void enqueue(
            Long workspaceId,
            Long documentId,
            KnowledgeIndexOutboxOperation operation,
            KnowledgeIndexOutboxPayload payload
    ) {
        repository.enqueue(eventKey(workspaceId, documentId, operation), workspaceId,
                documentId, operation, payload, OffsetDateTime.now());
    }

    /**
     * 每次业务变更使用独立消息键，允许灾后重新投递相同快照。
     * 消费端依靠固定的 OpenSearch 文档编号实现重复投递幂等。
     */
    private String eventKey(
            Long workspaceId,
            Long documentId,
            KnowledgeIndexOutboxOperation operation
    ) {
        return workspaceId + ":" + documentId + ":" + operation + ":" + UUID.randomUUID();
    }
}
