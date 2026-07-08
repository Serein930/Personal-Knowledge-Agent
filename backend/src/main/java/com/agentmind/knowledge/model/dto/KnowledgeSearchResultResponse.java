package com.agentmind.knowledge.model.dto;

/**
 * DTO returned for one retrieved chunk.
 *
 * <p>The score is cosine similarity from the current mock vector store. Later pgvector integration can keep the
 * same response shape while changing the score source.</p>
 */
public record KnowledgeSearchResultResponse(
        String chunkId,
        Long documentId,
        int chunkSequence,
        String headingPath,
        String content,
        double score
) {
}
