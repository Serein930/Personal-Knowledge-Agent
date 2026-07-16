package com.agentmind.evaluation.model.dto;

/** 同一道固定评估题在当前任务与基线任务之间的指标差值。 */
public record RagEvaluationCaseMetricDeltaResponse(
        String caseKey,
        double recallAtK,
        double reciprocalRank,
        double ndcgAtK,
        boolean citationCoverageChanged,
        boolean refusalCorrectnessChanged,
        double answerKeywordCoverage,
        double faithfulness,
        double answerRelevance,
        long elapsedMillis,
        int totalTokens
) {
}
