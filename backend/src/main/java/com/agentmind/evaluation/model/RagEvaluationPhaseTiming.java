package com.agentmind.evaluation.model;

/** 单题检索、重排、生成和端到端耗时快照，单位均为毫秒。 */
public record RagEvaluationPhaseTiming(
        long retrievalMillis,
        long rerankMillis,
        long generationMillis,
        long totalMillis
) {
}
