package com.agentmind.chat.service;

import com.agentmind.chat.model.dto.RagCitationResponse;
import java.util.List;

/**
 * Input passed to an answer generator.
 *
 * <p>The request keeps the user question, retrieved prompt context and normalized citations together. A later
 * Spring AI adapter can transform this object into a model prompt while tests can keep using the deterministic mock
 * generator.</p>
 */
public record AnswerGenerationRequest(
        String question,
        String promptContext,
        List<RagCitationResponse> citations
) {
}
