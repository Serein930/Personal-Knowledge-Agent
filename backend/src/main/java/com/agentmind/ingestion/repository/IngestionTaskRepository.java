package com.agentmind.ingestion.repository;

import com.agentmind.ingestion.model.IngestionTask;
import com.agentmind.ingestion.model.IngestionTaskStatus;
import com.agentmind.ingestion.model.IngestionTaskType;
import java.util.Optional;

/** 摄取任务仓储端口，使业务流程不依赖具体数据库实现。 */
public interface IngestionTaskRepository {

    IngestionTask create(Long ownerUserId, Long workspaceId, Long documentId,
            IngestionTaskType taskType, IngestionTaskStatus status, int progress, String source);

    void update(Long taskId, IngestionTaskStatus status, int progress, String source, String errorMessage);

    Optional<IngestionTask> findByWorkspaceIdAndId(Long workspaceId, Long taskId);
}
