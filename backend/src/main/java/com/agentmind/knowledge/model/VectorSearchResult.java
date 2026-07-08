package com.agentmind.knowledge.model;

/**
 * One semantic retrieval hit.
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
