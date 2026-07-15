package com.agentmind.evaluation.model;

import java.math.BigDecimal;
import java.util.List;

/** 单题评估证据，供问题定位和聚合指标复算。 */
public record RagEvaluationCaseResult(
        String caseKey,
        String question,
        List<RagEvaluationRetrievedSource> retrievedSources,
        int relevantRetrievedCount,
        int relevantExpectedCount,
        Integer firstRelevantRank,
        double recallAtK,
        double reciprocalRank,
        boolean citationCovered,
        boolean expectedRefusal,
        boolean actualRefusal,
        boolean refusalCorrect,
        double answerKeywordCoverage,
        long elapsedMillis,
        int promptTokens,
        int completionTokens,
        boolean tokenUsageEstimated,
        BigDecimal estimatedCostUsd
) {
}
