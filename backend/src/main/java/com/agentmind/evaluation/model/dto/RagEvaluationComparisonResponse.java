package com.agentmind.evaluation.model.dto;

import java.util.List;

/** 同一评估集版本的当前任务与基线任务对比。 */
public record RagEvaluationComparisonResponse(
        Long currentJobId,
        Long baselineJobId,
        boolean comparable,
        String message,
        RagEvaluationMetricDeltaResponse delta,
        List<RagEvaluationCaseMetricDeltaResponse> caseDeltas
) {
}
