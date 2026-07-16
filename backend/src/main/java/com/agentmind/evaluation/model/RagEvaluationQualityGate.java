package com.agentmind.evaluation.model;

import java.math.BigDecimal;

/**
 * 可选质量门禁阈值。
 *
 * <p>空值表示不检查该指标。质量指标使用最低阈值，耗时、Token 和成本使用最高阈值。</p>
 */
public record RagEvaluationQualityGate(
        Double minimumRecallAtK,
        Double minimumNdcgAtK,
        Double minimumFaithfulness,
        Double minimumAnswerRelevance,
        Long maximumAverageLatencyMillis,
        Integer maximumTotalTokens,
        BigDecimal maximumEstimatedCostUsd
) {

    public boolean configured() {
        return minimumRecallAtK != null || minimumNdcgAtK != null || minimumFaithfulness != null
                || minimumAnswerRelevance != null || maximumAverageLatencyMillis != null
                || maximumTotalTokens != null || maximumEstimatedCostUsd != null;
    }
}
