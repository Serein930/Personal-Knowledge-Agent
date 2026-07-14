package com.agentmind.agent.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 显式触发智能体工具的请求体。
 *
 * <p>requestId 是可选的调用幂等键。相同用户、知识空间和 requestId 的成功只读调用会复用既有审计结果。</p>
 */
public record AgentToolExecutionRequest(
        Long conversationId,

        @NotBlank(message = "工具名称不能为空")
        @Size(max = 100, message = "工具名称长度不能超过100")
        String toolName,

        @Size(max = 100, message = "请求编号长度不能超过100")
        String requestId,

        JsonNode arguments
) {
}
