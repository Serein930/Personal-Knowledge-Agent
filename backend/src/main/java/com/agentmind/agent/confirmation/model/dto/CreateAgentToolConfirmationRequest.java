package com.agentmind.agent.confirmation.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 创建写工具确认单的请求。
 *
 * <p>请求编号是写入幂等键，同一用户、知识空间、工具和请求编号只会复用一次成功执行结果。</p>
 */
public record CreateAgentToolConfirmationRequest(
        @Positive(message = "会话编号必须为正数")
        Long conversationId,

        @Positive(message = "消息编号必须为正数")
        Long messageId,

        @NotBlank(message = "工具名称不能为空")
        @Size(max = 100, message = "工具名称长度不能超过100")
        String toolName,

        @NotBlank(message = "请求编号不能为空")
        @Size(max = 100, message = "请求编号长度不能超过100")
        String requestId,

        JsonNode arguments
) {
}
