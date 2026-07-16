package com.agentmind.knowledge.outbox.model;

/** 一次 OpenSearch 索引重建任务的投递结果。 */
public record KnowledgeIndexRebuildResult(
        Long workspaceId,
        long documents,
        long chunks,
        long outboxEvents
) {
}
