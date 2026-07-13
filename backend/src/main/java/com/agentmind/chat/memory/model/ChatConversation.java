package com.agentmind.chat.memory.model;

import java.time.OffsetDateTime;

/**
 * 知识空间内的短期记忆会话。
 *
 * <p>会话必须携带知识空间编号。任何查询和更新都应同时校验会话编号与知识空间编号，
 * 不能只凭全局会话编号访问用户数据。</p>
 */
public record ChatConversation(
        Long id,
        Long workspaceId,
        String title,
        ChatConversationStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
