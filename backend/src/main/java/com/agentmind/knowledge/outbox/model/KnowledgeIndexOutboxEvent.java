package com.agentmind.knowledge.outbox.model;

import java.time.OffsetDateTime;

/** PostgreSQL 中的一条知识索引事务消息。 */
public record KnowledgeIndexOutboxEvent(
        Long id,
        String eventKey,
        Long workspaceId,
        Long documentId,
        KnowledgeIndexOutboxOperation operation,
        KnowledgeIndexOutboxPayload payload,
        KnowledgeIndexOutboxStatus status,
        int attempts,
        OffsetDateTime availableAt,
        String leaseOwner,
        OffsetDateTime leaseExpiresAt,
        String lastError,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt
) {
}
