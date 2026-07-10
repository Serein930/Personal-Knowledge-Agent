package com.agentmind.chat.model.dto;

import com.agentmind.agent.audit.model.dto.AgentToolCallSummaryResponse;
import java.util.List;

/**
 * Response contract for RAG chat.
 *
 * <p>The current answer is produced by a deterministic mock generator. The response shape already carries retrieval
 * context, citations, tool calls and token usage so a later Spring AI generator can replace the mock without changing
 * frontend contracts.</p>
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
