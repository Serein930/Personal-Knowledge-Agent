package com.agentmind.evaluation.model;

import java.math.BigDecimal;

/** 一次固定评估任务的聚合指标快照。 */
public record RagEvaluationMetrics(
        int caseCount,
        double recallAtK,
        double meanReciprocalRank,
        double ndcgAtK,
        double citationCoverage,
        double refusalAccuracy,
        double answerKeywordCoverage,
        double faithfulness,
        double answerRelevance,
        long averageRetrievalMillis,
        long averageRerankMillis,
        long averageGenerationMillis,
        long averageLatencyMillis,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        boolean tokenUsageEstimated,
        BigDecimal estimatedCostUsd
) {
}
