package com.agentmind.chat.memory.model.dto;

import com.agentmind.chat.memory.model.ChatConversationStatus;
import java.time.OffsetDateTime;

/**
 * 短期记忆会话查询响应。
 */
public record ChatConversationResponse(
        Long id,
        String title,
        ChatConversationStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
