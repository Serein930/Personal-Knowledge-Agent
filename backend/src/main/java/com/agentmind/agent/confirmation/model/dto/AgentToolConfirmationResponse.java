package com.agentmind.agent.confirmation.model.dto;

import com.agentmind.agent.confirmation.model.AgentToolConfirmationStatus;
import com.agentmind.agent.model.dto.AgentToolExecutionResponse;
import java.time.OffsetDateTime;

/**
 * 写工具确认单的安全响应视图。
 *
 * <p>该响应不包含完整工具参数和令牌摘要，只提供前端展示确认状态所需的信息。</p>
 */
public record AgentToolConfirmationResponse(
        Long id,
        Long workspaceId,
        Long conversationId,
        Long messageId,
        String requestId,
        String toolName,
        String argumentSummary,
        AgentToolConfirmationStatus status,
        AgentToolExecutionResponse execution,
        String failureReason,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        OffsetDateTime updatedAt,
        OffsetDateTime executedAt
) {
}
