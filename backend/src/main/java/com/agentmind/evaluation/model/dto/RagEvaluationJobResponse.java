package com.agentmind.evaluation.model.dto;

import com.agentmind.evaluation.model.RagEvaluationCaseResult;
import com.agentmind.evaluation.model.RagEvaluationExperimentConfig;
import com.agentmind.evaluation.model.RagEvaluationJobStatus;
import com.agentmind.evaluation.model.RagEvaluationMetrics;
import com.agentmind.evaluation.model.RagEvaluationQualityGate;
import com.agentmind.evaluation.model.RagEvaluationQualityGateResult;
import java.time.OffsetDateTime;
import java.util.List;

/** 异步评估任务详情、进度、实验快照、质量门禁与逐题证据响应。 */
public record RagEvaluationJobResponse(
        Long id,
        Long datasetId,
        int datasetVersion,
        RagEvaluationJobStatus status,
        String retrievalStrategy,
        int topK,
        String promptVersion,
        String modelName,
        RagEvaluationExperimentConfig experimentConfig,
        Long baselineJobId,
        Long retryOfJobId,
        int totalCases,
        int completedCases,
        int progress,
        boolean terminal,
        RagEvaluationMetrics metrics,
        RagEvaluationQualityGate qualityGate,
        RagEvaluationQualityGateResult qualityGateResult,
        List<RagEvaluationCaseResult> caseResults,
        String failureReason,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt
) {
}
