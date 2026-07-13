package com.agentmind.chat.memory.repository.redis;

/**
 * Redis 中保存的会话序列化文档。
 *
 * <p>编号使用字符串保存，避免 Lua 的双精度数字在处理超大长整型编号时损失精度。
 * {@code schemaVersion} 用于识别持久化结构，版本不兼容时适配器会明确拒绝读取。</p>
 */
record RedisChatConversationDocument(
        int schemaVersion,
        String id,
        String workspaceId,
        String title,
        String status,
        String createdAt,
        String updatedAt
) {
}
