package com.agentmind.ingestion.model;

import java.time.OffsetDateTime;

/** 摄取任务的持久化快照，包含权限隔离所需的用户和知识空间归属。 */
public record IngestionTask(
        Long id,
        Long ownerUserId,
        Long workspaceId,
        Long documentId,
        IngestionTaskType taskType,
        IngestionTaskStatus status,
        int progress,
        String source,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
) {
}
