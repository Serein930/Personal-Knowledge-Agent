package com.agentmind.evaluation.model;

import java.math.BigDecimal;

/** 一次固定评估任务的聚合指标快照。 */
public record RagEvaluationMetrics(
        int caseCount,
        double recallAtK,
        double meanReciprocalRank,
        double citationCoverage,
        double refusalAccuracy,
        double answerKeywordCoverage,
        long averageLatencyMillis,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        boolean tokenUsageEstimated,
        BigDecimal estimatedCostUsd
) {
}
