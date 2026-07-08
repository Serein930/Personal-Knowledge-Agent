package com.agentmind.ingestion.model.dto;

import com.agentmind.ingestion.model.IngestionTaskStatus;
import com.agentmind.ingestion.model.IngestionTaskType;
import java.time.OffsetDateTime;

/**
 * 摄取任务响应 DTO。
 *
 * <p>前端采集中心使用该结构展示任务进度。errorMessage 只返回可给用户或开发者理解的
 * 简短错误摘要，内部异常堆栈应保留在服务端日志中。</p>
 */
public record IngestionTaskResponse(
        Long id,
        Long documentId,
        IngestionTaskType taskType,
        IngestionTaskStatus status,
        int progress,
        String source,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
