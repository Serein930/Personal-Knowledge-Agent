package com.agentmind.chat.memory.model.dto;

import com.agentmind.chat.memory.model.ChatMessageRole;
import com.agentmind.chat.memory.model.ChatMessageStatus;
import java.time.OffsetDateTime;

/**
 * 短期记忆消息查询响应。
 *
 * <p>失败和取消消息会返回状态与失败原因，但正文为空，前端可以展示失败标记而不会误用半截回答。</p>
 */
public record ChatMessageResponse(
        Long id,
        ChatMessageRole role,
        ChatMessageStatus status,
        String content,
        String failureReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
