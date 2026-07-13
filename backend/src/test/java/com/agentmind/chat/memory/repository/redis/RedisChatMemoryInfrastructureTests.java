package com.agentmind.chat.memory.repository.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentmind.chat.memory.config.ChatMemoryProperties;
import com.agentmind.chat.memory.model.ChatConversation;
import com.agentmind.chat.memory.model.ChatConversationStatus;
import com.agentmind.chat.memory.model.ChatMessage;
import com.agentmind.chat.memory.model.ChatMessageRole;
import com.agentmind.chat.memory.model.ChatMessageStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/**
 * Redis 会话记忆基础设施组件的纯单元测试。
 *
 * <p>该测试不连接 Redis，负责提前发现键空间隔离和序列化版本控制中的回归。</p>
 */
class RedisChatMemoryInfrastructureTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void keysShouldContainVersionAndWorkspaceHashTag() {
        ChatMemoryProperties properties = properties(3);
        RedisChatMemoryKeyFactory keyFactory = new RedisChatMemoryKeyFactory(properties);

        assertThat(keyFactory.conversation(100L, 200L))
                .isEqualTo("agentmind:test:chat-memory:v3:workspace:{100}:conversation:200");
        assertThat(keyFactory.messageHash(100L, 200L)).contains("workspace:{100}");
        assertThat(keyFactory.messageHash(101L, 200L)).contains("workspace:{101}");
        assertThat(keyFactory.messageHash(100L, 200L))
                .isNotEqualTo(keyFactory.messageHash(101L, 200L));
    }

    @Test
    void codecShouldRoundTripConversationAndMessage() {
        ChatMemoryProperties properties = properties(1);
        RedisChatMemoryCodec codec = new RedisChatMemoryCodec(objectMapper, properties);
        OffsetDateTime now = OffsetDateTime.now();
        ChatConversation conversation = new ChatConversation(
                10L,
                20L,
                "Redis 会话",
                ChatConversationStatus.ACTIVE,
                now,
                now
        );
        ChatMessage message = new ChatMessage(
                30L,
                20L,
                10L,
                ChatMessageRole.ASSISTANT,
                ChatMessageStatus.COMPLETED,
                "完整回答",
                "",
                now,
                now
        );

        assertThat(codec.readConversation(codec.writeConversation(conversation))).isEqualTo(conversation);
        assertThat(codec.readMessage(codec.writeMessage(message))).isEqualTo(message);
    }

    @Test
    void codecShouldRejectUnsupportedSerializationVersion() {
        RedisChatMemoryCodec versionOneCodec = new RedisChatMemoryCodec(objectMapper, properties(1));
        RedisChatMemoryCodec versionTwoCodec = new RedisChatMemoryCodec(objectMapper, properties(2));
        OffsetDateTime now = OffsetDateTime.now();
        ChatMessage message = new ChatMessage(
                30L,
                20L,
                10L,
                ChatMessageRole.USER,
                ChatMessageStatus.COMPLETED,
                "问题",
                "",
                now,
                now
        );
        String versionOneJson = versionOneCodec.writeMessage(message);

        assertThatThrownBy(() -> versionTwoCodec.readMessage(versionOneJson))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis 消息数据版本不受支持，期望版本为 2，实际版本为 1");
    }

    private ChatMemoryProperties properties(int version) {
        ChatMemoryProperties properties = new ChatMemoryProperties();
        properties.setKeyPrefix("agentmind:test:chat-memory");
        properties.setSerializationVersion(version);
        return properties;
    }
}
