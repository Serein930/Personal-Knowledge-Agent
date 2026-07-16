package com.agentmind.evaluation.model;

/** 单题检索命中来源及其排名。 */
public record RagEvaluationRetrievedSource(
        String chunkId,
        Long documentId,
        int rank,
        double score,
        String content
) {
}
