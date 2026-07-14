package com.agentmind.agent.confirmation.model.dto;

/**
 * 确认或拒绝写工具后的响应。
 *
 * <p>reused 为真表示当前请求复用了同一确认单已经完成的结果，没有再次执行写操作。</p>
 */
public record DecidedAgentToolConfirmationResponse(
        AgentToolConfirmationResponse confirmation,
        boolean reused
) {
}
