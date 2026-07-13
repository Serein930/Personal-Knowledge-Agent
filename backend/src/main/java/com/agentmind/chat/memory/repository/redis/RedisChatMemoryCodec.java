package com.agentmind.chat.memory.repository.redis;

import com.agentmind.chat.memory.config.ChatMemoryProperties;
import com.agentmind.chat.memory.model.ChatConversation;
import com.agentmind.chat.memory.model.ChatConversationStatus;
import com.agentmind.chat.memory.model.ChatMessage;
import com.agentmind.chat.memory.model.ChatMessageRole;
import com.agentmind.chat.memory.model.ChatMessageStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;

/**
 * 会话领域模型与版本化 Redis 文档之间的编解码器。
 *
 * <p>序列化细节集中在基础设施适配层，领域模型和 {@code ChatMemoryRepository} 不感知 JSON，
 * 后续调整 Redis 文档结构时可以通过提升版本并编写迁移逻辑完成兼容。</p>
 */
final class RedisChatMemoryCodec {

    private final ObjectMapper objectMapper;
    private final int serializationVersion;

    RedisChatMemoryCodec(ObjectMapper objectMapper, ChatMemoryProperties properties) {
        this.objectMapper = objectMapper;
        this.serializationVersion = properties.getSerializationVersion();
    }

    String writeConversation(ChatConversation conversation) {
        RedisChatConversationDocument document = new RedisChatConversationDocument(
                serializationVersion,
                conversation.id().toString(),
                conversation.workspaceId().toString(),
                conversation.title(),
                conversation.status().name(),
                conversation.createdAt().toString(),
                conversation.updatedAt().toString()
        );
        return write(document, "会话");
    }

    ChatConversation readConversation(String json) {
        try {
            RedisChatConversationDocument document = objectMapper.readValue(
                    json,
                    RedisChatConversationDocument.class
            );
            requireSupportedVersion(document.schemaVersion(), "会话");
            return new ChatConversation(
                    Long.valueOf(document.id()),
                    Long.valueOf(document.workspaceId()),
                    document.title(),
                    ChatConversationStatus.valueOf(document.status()),
                    OffsetDateTime.parse(document.createdAt()),
                    OffsetDateTime.parse(document.updatedAt())
            );
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("Redis 会话数据无法反序列化", exception);
        }
    }

    String writeMessage(ChatMessage message) {
        RedisChatMessageDocument document = new RedisChatMessageDocument(
                serializationVersion,
                message.id().toString(),
                message.workspaceId().toString(),
                message.conversationId().toString(),
                message.role().name(),
                message.status().name(),
                message.content(),
                message.failureReason(),
                message.createdAt().toString(),
                message.updatedAt().toString()
        );
        return write(document, "消息");
    }

    ChatMessage readMessage(String json) {
        try {
            RedisChatMessageDocument document = objectMapper.readValue(json, RedisChatMessageDocument.class);
            requireSupportedVersion(document.schemaVersion(), "消息");
            return new ChatMessage(
                    Long.valueOf(document.id()),
                    Long.valueOf(document.workspaceId()),
                    Long.valueOf(document.conversationId()),
                    ChatMessageRole.valueOf(document.role()),
                    ChatMessageStatus.valueOf(document.status()),
                    document.content(),
                    document.failureReason(),
                    OffsetDateTime.parse(document.createdAt()),
                    OffsetDateTime.parse(document.updatedAt())
            );
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("Redis 消息数据无法反序列化", exception);
        }
    }

    private String write(Object document, String dataType) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Redis " + dataType + "数据无法序列化", exception);
        }
    }

    private void requireSupportedVersion(int actualVersion, String dataType) {
        if (actualVersion != serializationVersion) {
            throw new IllegalStateException(
                    "Redis " + dataType + "数据版本不受支持，期望版本为 "
                            + serializationVersion + "，实际版本为 " + actualVersion
            );
        }
    }
}
