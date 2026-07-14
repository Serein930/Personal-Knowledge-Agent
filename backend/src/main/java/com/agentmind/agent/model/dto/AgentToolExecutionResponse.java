package com.agentmind.agent.model.dto;

import com.agentmind.agent.audit.model.dto.AgentToolCallSummaryResponse;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 智能体工具执行结果响应。
 */
public record AgentToolExecutionResponse(
        AgentToolCallSummaryResponse audit,
        JsonNode result,
        boolean reused
) {
}
