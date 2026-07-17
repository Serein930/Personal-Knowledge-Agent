package com.agentmind.knowledge.vector.observability;

import java.math.BigDecimal;

/** 单次向量模型批量调用的脱敏观测数据。 */
public record EmbeddingCallObservation(
        String modelName,
        int inputCount,
        int inputTokens,
        BigDecimal estimatedCostUsd,
        long durationMillis,
        int attempts,
        boolean succeeded,
        String failureType
) {
}
