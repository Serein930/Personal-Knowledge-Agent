package com.agentmind.agent.audit.model.dto;

import com.agentmind.agent.audit.model.AgentToolCallStatus;
import com.agentmind.agent.audit.model.AgentToolType;

/**
 * 智能体工具调用摘要响应数据传输对象。
 *
 * <p>检索增强生成问答响应和评估观测页都可以复用该结构展示工具链路。
 * 这里不返回完整请求载荷，避免把潜在敏感数据直接暴露给前端。</p>
 */
public record AgentToolCallSummaryResponse(
        String toolName,
        AgentToolType toolType,
        AgentToolCallStatus status,
        String responseSummary,
        long latencyMs
) {
}
