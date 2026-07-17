package com.agentmind.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 使用真实 Redis 验证多次扣减共享同一个原子配额窗口。 */
@Tag("redis")
@EnabledIfEnvironmentVariable(named = "AGENTMIND_REDIS_INTEGRATION_TEST", matches = "true")
class RedisDistributedRateLimiterIntegrationTests {

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RateLimitProperties properties;
    private RedisDistributedRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration("localhost", 6379);
        configuration.setDatabase(14);
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        properties = new RateLimitProperties();
        properties.setKeyPrefix("agentmind:test:rate-limit:" + UUID.randomUUID());
        properties.setWindow(Duration.ofSeconds(30));
        rateLimiter = new RedisDistributedRateLimiter(redisTemplate, properties);
    }

    @AfterEach
    void tearDown() {
        if (redisTemplate != null && properties != null) {
            Set<String> keys = redisTemplate.keys(properties.getKeyPrefix() + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void requestsShouldShareAtomicQuotaAndExposeRetryTime() {
        assertThat(rateLimiter.tryAcquire(RateLimitScope.RAG, "user:1", 2).allowed()).isTrue();
        assertThat(rateLimiter.tryAcquire(RateLimitScope.RAG, "user:1", 2).remaining()).isZero();

        RateLimitDecision rejected = rateLimiter.tryAcquire(RateLimitScope.RAG, "user:1", 2);

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isBetween(1L, 30L);
        assertThat(rateLimiter.tryAcquire(RateLimitScope.RAG, "user:2", 2).allowed()).isTrue();
    }
}
