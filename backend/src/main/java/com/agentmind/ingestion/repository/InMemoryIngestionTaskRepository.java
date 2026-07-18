package com.agentmind.ingestion.repository;

import com.agentmind.ingestion.model.IngestionTask;
import com.agentmind.ingestion.model.IngestionTaskStatus;
import com.agentmind.ingestion.model.IngestionTaskType;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** 零依赖开发和单元测试使用的线程安全摄取任务仓储。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.core.persistence", name = "store", havingValue = "memory", matchIfMissing = true)
public class InMemoryIngestionTaskRepository implements IngestionTaskRepository {

    private final AtomicLong idGenerator = new AtomicLong(1000);
    private final Map<Long, IngestionTask> tasks = new ConcurrentHashMap<>();

    @Override
    public IngestionTask create(Long ownerUserId, Long workspaceId, Long documentId,
            IngestionTaskType taskType, IngestionTaskStatus status, int progress, String source) {
        OffsetDateTime now = OffsetDateTime.now();
        IngestionTask task = new IngestionTask(idGenerator.incrementAndGet(), ownerUserId, workspaceId,
                documentId, taskType, status, progress, source, null, now, now,
                status == IngestionTaskStatus.RUNNING ? now : null,
                isFinished(status) ? now : null);
        tasks.put(task.id(), task);
        return task;
    }

    @Override
    public void update(Long taskId, IngestionTaskStatus status, int progress, String source, String errorMessage) {
        tasks.computeIfPresent(taskId, (id, current) -> new IngestionTask(current.id(), current.ownerUserId(),
                current.workspaceId(), current.documentId(), current.taskType(), status, progress, source,
                errorMessage, current.createdAt(), OffsetDateTime.now(),
                current.startedAt() == null && status == IngestionTaskStatus.RUNNING
                        ? OffsetDateTime.now() : current.startedAt(),
                isFinished(status) ? OffsetDateTime.now() : current.finishedAt()));
    }

    @Override
    public Optional<IngestionTask> findByWorkspaceIdAndId(Long workspaceId, Long taskId) {
        return Optional.ofNullable(tasks.get(taskId)).filter(task -> workspaceId.equals(task.workspaceId()));
    }

    private boolean isFinished(IngestionTaskStatus status) {
        return status == IngestionTaskStatus.SUCCEEDED || status == IngestionTaskStatus.FAILED;
    }
}
