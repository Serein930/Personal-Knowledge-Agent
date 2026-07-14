package com.agentmind.agent.confirmation.model.dto;

/**
 * 新建确认单响应。
 *
 * <p>确认令牌只在本响应中返回一次，后续查询确认单不会再次返回。</p>
 */
public record CreatedAgentToolConfirmationResponse(
        AgentToolConfirmationResponse confirmation,
        String confirmationToken
) {
}
