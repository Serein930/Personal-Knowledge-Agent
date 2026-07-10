package com.agentmind.knowledge.model;

/**
 * 单条语义检索命中结果。
 */
public record VectorSearchResult(
        String chunkId,
        Long documentId,
        int chunkSequence,
        String headingPath,
        String content,
        double score
) {
}
