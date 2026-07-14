package com.agentmind.chat.model.dto;

import com.agentmind.agent.audit.model.dto.AgentToolCallSummaryResponse;

/**
 * 流式问答中的工具调用摘要事件。
 *
 * <p>该事件不包含完整知识片段或工具原始参数，只返回已经脱敏的审计摘要。
 * 前端可据此展示本次回答使用了哪些工具以及工具是否成功。</p>
 */
public record RagStreamToolCallEvent(
        int sequence,
        AgentToolCallSummaryResponse toolCall
) {
}
