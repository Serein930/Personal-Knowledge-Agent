package com.agentmind.chat.memory.repository.redis;

/**
 * Redis 中保存的消息序列化文档。
 *
 * <p>该文档显式保存知识空间和会话归属，即使键已经完成隔离，反序列化后仍会再次校验归属，
 * 防止错误键或人工写入的数据越过应用层边界。</p>
 */
record RedisChatMessageDocument(
        int schemaVersion,
        String id,
        String workspaceId,
        String conversationId,
        String role,
        String status,
        String content,
        String failureReason,
        String createdAt,
        String updatedAt
) {
}
