package com.agentmind.chat.model.dto;

import com.agentmind.agent.confirmation.model.dto.CreatedAgentToolConfirmationResponse;

/**
 * 流式回答中的写工具待确认事件。
 *
 * <p>事件只创建确认单，不执行写工具。确认令牌仅在本事件中出现一次，前端应保存在当前页面内存中，
 * 并在用户点击确认或拒绝时立即提交。</p>
 */
public record RagStreamToolConfirmationRequiredEvent(
        int sequence,
        CreatedAgentToolConfirmationResponse proposal
) {
}
