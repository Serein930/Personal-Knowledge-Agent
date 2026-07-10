package com.agentmind.chat.model.dto;

import com.agentmind.agent.audit.model.dto.AgentToolCallSummaryResponse;
import java.util.List;

/**
 * RAG 问答响应。
 *
 * <p>当前回答由可配置的回答生成器产生，默认使用确定性模拟实现。响应中已经保留检索上下文、
 * 引用来源、工具调用摘要和令牌用量，后续替换为真实模型时无需改变前端契约。</p>
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
