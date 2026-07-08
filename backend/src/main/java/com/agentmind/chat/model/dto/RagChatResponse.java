package com.agentmind.chat.model.dto;

import com.agentmind.agent.audit.model.dto.AgentToolCallSummaryResponse;
import java.util.List;

/**
 * RAG 问答响应 DTO。
 *
 * <p>该结构同时承载回答正文、引用来源、工具调用摘要和 Token 用量。
 * 后续如果实现 SSE 流式响应，可继续复用 citation、toolCalls 和 usage 的子结构。</p>
 */
public record RagChatResponse(
        Long conversationId,
        Long messageId,
        String answer,
        List<RagCitationResponse> citations,
        List<AgentToolCallSummaryResponse> toolCalls,
        TokenUsageResponse usage
) {
}
