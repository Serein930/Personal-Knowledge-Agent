package com.agentmind.evaluation.model.dto;

import com.agentmind.evaluation.model.RagEvaluationMetrics;
import com.agentmind.evaluation.model.RagEvaluationQualityGateStatus;
import java.time.OffsetDateTime;

/** 一次成功评估在趋势图中的不可变数据点。 */
public record RagEvaluationTrendPointResponse(
        Long jobId,
        int datasetVersion,
        String experimentName,
        String retrievalStrategy,
        String rerankStrategy,
        RagEvaluationMetrics metrics,
        RagEvaluationQualityGateStatus qualityGateStatus,
        OffsetDateTime completedAt
) {
}
