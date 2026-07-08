package com.agentmind.knowledge.model.dto;

import java.util.List;

/**
 * Semantic search response for development and frontend integration.
 */
public record KnowledgeSearchResponse(
        String query,
        int topK,
        List<KnowledgeSearchResultResponse> results
) {
}
