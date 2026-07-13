package com.agentmind.chat.memory.repository.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.chat.memory.config.ChatMemoryProperties;
import com.agentmind.chat.memory.model.ChatConversation;
import com.agentmind.chat.memory.model.ChatMessage;
import com.agentmind.chat.memory.model.ChatMessageRole;
import com.agentmind.chat.memory.model.ChatMessageStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 会话记忆适配器的手动集成测试。
 *
 * <p>测试默认跳过，只有环境变量 {@code AGENTMIND_REDIS_INTEGRATION_TEST=true} 时才连接本地
 * Redis 容器。测试使用独立数据库和随机键前缀，不会接触开发环境的正式会话键。</p>
 */
@Tag("redis")
@EnabledIfEnvironmentVariable(named = "AGENTMIND_REDIS_INTEGRATION_TEST", matches = "true")
class RedisChatMemoryRepositoryIntegrationTests {

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private ChatMemoryProperties properties;
    private RedisChatMemoryRepository repository;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration redisConfiguration = new RedisStandaloneConfiguration("localhost", 6379);
        redisConfiguration.setDatabase(15);
        connectionFactory = new LettuceConnectionFactory(redisConfiguration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        clearTestDatabase();

        properties = new ChatMemoryProperties();
        properties.setStore("redis");
        properties.setKeyPrefix("agentmind:test:chat-memory:" + UUID.randomUUID());
        properties.setSerializationVersion(1);
        properties.setTtl(Duration.ofMinutes(2));
        repository = new RedisChatMemoryRepository(redisTemplate, new ObjectMapper(), properties);
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            clearTestDatabase();
            connectionFactory.destroy();
        }
    }

    @Test
    void repositoryShouldPersistIsolateAndRefreshConversationData() {
        ChatConversation conversation = repository.createConversation(100L, "Redis 联调会话");
        repository.createMessage(
                100L,
                conversation.id(),
                ChatMessageRole.USER,
                ChatMessageStatus.COMPLETED,
                "什么是 Redis 会话记忆？"
        );
        ChatMessage assistant = repository.createMessage(
                100L,
                conversation.id(),
                ChatMessageRole.ASSISTANT,
                ChatMessageStatus.PENDING,
                ""
        );
        repository.transitionPendingMessage(
                100L,
                conversation.id(),
                assistant.id(),
                ChatMessageStatus.COMPLETED,
                "它用于跨实例保存短期上下文。",
                ""
        );

        RedisChatMemoryRepository restartedRepository = new RedisChatMemoryRepository(
                redisTemplate,
                new ObjectMapper(),
                properties
        );
        List<ChatMessage> messages = restartedRepository.findMessagesByWorkspaceIdAndConversationId(
                100L,
                conversation.id(),
                0,
                20
        );

        assertThat(messages).hasSize(2);
        assertThat(messages.getLast().status()).isEqualTo(ChatMessageStatus.COMPLETED);
        assertThat(messages.getLast().content()).isEqualTo("它用于跨实例保存短期上下文。");
        assertThat(restartedRepository.findConversationByWorkspaceIdAndId(200L, conversation.id())).isEmpty();
        assertThat(restartedRepository.findConversationsByWorkspaceId(100L, 0, 20)).hasSize(1);

        RedisChatMemoryKeyFactory keyFactory = new RedisChatMemoryKeyFactory(properties);
        Long remainingTtl = redisTemplate.getExpire(
                keyFactory.messageHash(100L, conversation.id()),
                TimeUnit.SECONDS
        );
        assertThat(remainingTtl).isNotNull().isPositive().isLessThanOrEqualTo(120L);
    }

    @Test
    void pendingMessageShouldReachOnlyOneFinalStateUnderConcurrency() {
        ChatConversation conversation = repository.createConversation(100L, "原子迁移会话");
        ChatMessage assistant = repository.createMessage(
                100L,
                conversation.id(),
                ChatMessageRole.ASSISTANT,
                ChatMessageStatus.PENDING,
                ""
        );
        RedisChatMemoryRepository secondRepository = new RedisChatMemoryRepository(
                redisTemplate,
                new ObjectMapper(),
                properties
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<ChatMessage> completed = CompletableFuture.supplyAsync(
                    () -> repository.transitionPendingMessage(
                            100L,
                            conversation.id(),
                            assistant.id(),
                            ChatMessageStatus.COMPLETED,
                            "完整回答",
                            ""
                    ).orElseThrow(),
                    executor
            );
            CompletableFuture<ChatMessage> failed = CompletableFuture.supplyAsync(
                    () -> secondRepository.transitionPendingMessage(
                            100L,
                            conversation.id(),
                            assistant.id(),
                            ChatMessageStatus.FAILED,
                            "",
                            "模型调用失败"
                    ).orElseThrow(),
                    executor
            );
            CompletableFuture.allOf(completed, failed).join();

            ChatMessage finalMessage = repository.findMessageByWorkspaceIdAndConversationIdAndId(
                    100L,
                    conversation.id(),
                    assistant.id()
            ).orElseThrow();
            assertThat(completed.join().status()).isEqualTo(finalMessage.status());
            assertThat(failed.join().status()).isEqualTo(finalMessage.status());
            if (finalMessage.status() == ChatMessageStatus.COMPLETED) {
                assertThat(finalMessage.content()).isEqualTo("完整回答");
                assertThat(finalMessage.failureReason()).isEmpty();
            } else {
                assertThat(finalMessage.status()).isEqualTo(ChatMessageStatus.FAILED);
                assertThat(finalMessage.content()).isEmpty();
                assertThat(finalMessage.failureReason()).isEqualTo("模型调用失败");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private void clearTestDatabase() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }
}
