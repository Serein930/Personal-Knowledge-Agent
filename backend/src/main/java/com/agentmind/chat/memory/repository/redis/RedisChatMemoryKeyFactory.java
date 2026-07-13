package com.agentmind.chat.memory.repository.redis;

import com.agentmind.chat.memory.config.ChatMemoryProperties;

/**
 * Redis 会话记忆键生成器。
 *
 * <p>同一知识空间的业务键都包含相同的 Redis 集群哈希标签，例如 {@code {100}}。
 * 这样消息终态迁移涉及的多个键会落在同一个哈希槽，后续切换 Redis 集群时仍可执行 Lua 原子脚本。</p>
 */
final class RedisChatMemoryKeyFactory {

    private final String namespace;

    RedisChatMemoryKeyFactory(ChatMemoryProperties properties) {
        String keyPrefix = properties.getKeyPrefix() == null ? "" : properties.getKeyPrefix().trim();
        while (keyPrefix.endsWith(":")) {
            keyPrefix = keyPrefix.substring(0, keyPrefix.length() - 1);
        }
        if (keyPrefix.isBlank()) {
            throw new IllegalArgumentException("Redis 会话记忆键前缀不能为空");
        }
        if (properties.getSerializationVersion() < 1) {
            throw new IllegalArgumentException("Redis 会话记忆序列化版本必须大于零");
        }
        this.namespace = keyPrefix + ":v" + properties.getSerializationVersion();
    }

    String conversationSequence() {
        return namespace + ":sequence:conversation";
    }

    String messageSequence() {
        return namespace + ":sequence:message";
    }

    String conversationIndex(Long workspaceId) {
        return workspaceBase(workspaceId) + ":conversations";
    }

    String conversation(Long workspaceId, Long conversationId) {
        return workspaceBase(workspaceId) + ":conversation:" + conversationId;
    }

    String messageHash(Long workspaceId, Long conversationId) {
        return conversation(workspaceId, conversationId) + ":messages";
    }

    String messageOrder(Long workspaceId, Long conversationId) {
        return conversation(workspaceId, conversationId) + ":message-order";
    }

    private String workspaceBase(Long workspaceId) {
        return namespace + ":workspace:{" + workspaceId + "}";
    }
}
