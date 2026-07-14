package com.agentmind.agent.confirmation.model;

import com.agentmind.agent.model.dto.AgentToolExecutionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

/**
 * 写工具确认单领域模型。
 *
 * <p>服务端只保存确认令牌摘要，不保存令牌明文。工具参数仅供确认后执行使用，控制层响应会转换为
 * 专用 DTO，避免把内部令牌摘要和完整参数无意返回给客户端。</p>
 */
public record AgentToolConfirmation(
        Long id,
        Long ownerUserId,
        Long workspaceId,
        Long conversationId,
        Long messageId,
        String requestId,
        String toolName,
        JsonNode arguments,
        String argumentSummary,
        String tokenDigest,
        AgentToolConfirmationStatus status,
        AgentToolExecutionResponse executionResponse,
        String failureReason,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        OffsetDateTime updatedAt,
        OffsetDateTime executedAt
) {

    public AgentToolConfirmation withId(Long generatedId) {
        return new AgentToolConfirmation(
                generatedId, ownerUserId, workspaceId, conversationId, messageId, requestId, toolName,
                arguments, argumentSummary, tokenDigest, status, executionResponse, failureReason,
                createdAt, expiresAt, updatedAt, executedAt
        );
    }

    public AgentToolConfirmation transitionTo(AgentToolConfirmationStatus targetStatus, OffsetDateTime now) {
        return new AgentToolConfirmation(
                id, ownerUserId, workspaceId, conversationId, messageId, requestId, toolName,
                arguments, argumentSummary, tokenDigest, targetStatus, executionResponse, failureReason,
                createdAt, expiresAt, now, executedAt
        );
    }

    public AgentToolConfirmation succeed(AgentToolExecutionResponse response, OffsetDateTime now) {
        return new AgentToolConfirmation(
                id, ownerUserId, workspaceId, conversationId, messageId, requestId, toolName,
                arguments, argumentSummary, tokenDigest, AgentToolConfirmationStatus.SUCCEEDED,
                response, null, createdAt, expiresAt, now, now
        );
    }

    public AgentToolConfirmation fail(String reason, OffsetDateTime now) {
        return new AgentToolConfirmation(
                id, ownerUserId, workspaceId, conversationId, messageId, requestId, toolName,
                arguments, argumentSummary, tokenDigest, AgentToolConfirmationStatus.FAILED,
                executionResponse, reason, createdAt, expiresAt, now, executedAt
        );
    }
}
