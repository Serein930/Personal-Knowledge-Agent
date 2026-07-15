package com.agentmind.evaluation.model.dto;

import com.agentmind.evaluation.model.RagEvaluationCaseResult;
import com.agentmind.evaluation.model.RagEvaluationJobStatus;
import com.agentmind.evaluation.model.RagEvaluationMetrics;
import java.time.OffsetDateTime;
import java.util.List;

/** 评估任务详情与逐题证据响应。 */
public record RagEvaluationJobResponse(
        Long id,
        Long datasetId,
        int datasetVersion,
        RagEvaluationJobStatus status,
        String retrievalStrategy,
        int topK,
        String promptVersion,
        String modelName,
        Long baselineJobId,
        RagEvaluationMetrics metrics,
        List<RagEvaluationCaseResult> caseResults,
        String failureReason,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {
}
