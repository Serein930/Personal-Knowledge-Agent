package com.agentmind.chat.model.dto;

/**
 * Token usage summary.
 *
 * <p>Values stay zero in the retrieval-only stage because no model call is made. Later model adapters should fill
 * this structure from provider usage metadata.</p>
 */
public record TokenUsageResponse(
        int promptTokens,
        int completionTokens,
        int totalTokens
) {
}
