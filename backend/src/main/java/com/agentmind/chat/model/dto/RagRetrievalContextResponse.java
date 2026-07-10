package com.agentmind.chat.model.dto;

import java.util.List;

/**
 * Retrieval context prepared for answer generation.
 *
 * <p>`promptContext` is intentionally plain text and citation-indexed, making it easy to inspect in tests and to
 * pass into a later prompt template. The context should contain only retrieved private knowledge, never fabricated
 * facts.</p>
 */
public record RagRetrievalContextResponse(
        String question,
        int topK,
        String promptContext,
        List<RagCitationResponse> citations
) {
}
