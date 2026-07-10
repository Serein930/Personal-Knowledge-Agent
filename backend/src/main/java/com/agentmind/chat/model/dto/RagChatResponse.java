package com.agentmind.chat.model.dto;

import com.agentmind.agent.audit.model.dto.AgentToolCallSummaryResponse;
import java.util.List;

/**
 * Response contract for RAG chat.
 *
 * <p>Stage 6 preparation returns retrieval context and citations without calling a model. The `answer` field is a
 * deterministic placeholder so frontend integration can start before Spring AI chat generation is wired in.</p>
 */
public record RagChatResponse(
        Long conversationId,
        Long messageId,
        String answer,
        RagRetrievalContextResponse retrievalContext,
        List<RagCitationResponse> citations,
        List<AgentToolCallSummaryResponse> toolCalls,
        TokenUsageResponse usage
) {
}
