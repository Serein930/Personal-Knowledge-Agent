package com.agentmind.agent.confirmation.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 确认或拒绝写工具时提交的一次性令牌。
 */
public record AgentToolConfirmationTokenRequest(
        @NotBlank(message = "确认令牌不能为空")
        @Size(max = 200, message = "确认令牌长度不能超过200")
        String confirmationToken
) {
}
