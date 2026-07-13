package com.agentmind.chat.memory.model;

import java.time.OffsetDateTime;

/**
 * 短期记忆中的单条会话消息。
 *
 * <p>失败和取消的助手消息保留状态及失败原因，但正文保持为空，防止半截回答或异常文本
 * 被后续滑动窗口重新放入模型提示词。</p>
 */
public record ChatMessage(
        Long id,
        Long workspaceId,
        Long conversationId,
        ChatMessageRole role,
        ChatMessageStatus status,
        String content,
        String failureReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
