package com.agentmind.evaluation.model.dto;

import java.math.BigDecimal;

/** 当前任务相对于基线任务的指标差值，正值表示当前值更高。 */
public record RagEvaluationMetricDeltaResponse(
        double recallAtK,
        double meanReciprocalRank,
        double citationCoverage,
        double refusalAccuracy,
        double answerKeywordCoverage,
        long averageLatencyMillis,
        int totalTokens,
        BigDecimal estimatedCostUsd
) {
}
