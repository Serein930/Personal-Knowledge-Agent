package com.agentmind.knowledge.outbox.repository;

import com.agentmind.knowledge.outbox.model.KnowledgeIndexOutboxEvent;
import com.agentmind.knowledge.outbox.model.KnowledgeIndexOutboxOperation;
import com.agentmind.knowledge.outbox.model.KnowledgeIndexOutboxPayload;
import com.agentmind.knowledge.outbox.model.KnowledgeIndexOutboxStatistics;
import java.time.OffsetDateTime;
import java.util.List;

/** 知识索引事务消息持久化端口。 */
public interface KnowledgeIndexOutboxRepository {

    void enqueue(
            String eventKey,
            Long workspaceId,
            Long documentId,
            KnowledgeIndexOutboxOperation operation,
            KnowledgeIndexOutboxPayload payload,
            OffsetDateTime now
    );

    /**
     * 原子领取到期消息，同时回收租约已过期的处理中消息。
     * 多实例通过数据库行锁和跳过已锁定行并行消费。
     */
    default List<KnowledgeIndexOutboxEvent> claimBatch(
            String leaseOwner,
            OffsetDateTime now,
            OffsetDateTime leaseExpiresAt,
            int limit
    ) {
        return claimBatch(null, leaseOwner, now, leaseExpiresAt, limit);
    }

    List<KnowledgeIndexOutboxEvent> claimBatch(
            Long workspaceId,
            String leaseOwner,
            OffsetDateTime now,
            OffsetDateTime leaseExpiresAt,
            int limit
    );

    boolean markCompleted(Long eventId, String leaseOwner, OffsetDateTime now);

    boolean markFailed(
            Long eventId,
            String leaseOwner,
            String reason,
            OffsetDateTime nextAvailableAt,
            boolean dead,
            OffsetDateTime now
    );

    /** workspaceId 为空时返回全局运维统计，否则只统计指定知识空间。 */
    KnowledgeIndexOutboxStatistics statistics(Long workspaceId);
}
