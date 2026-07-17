package com.agentmind.common.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 基于 Redis 固定窗口的分布式限流适配器。
 *
 * <p>计数、自增和首次过期时间设置由一段 Lua 脚本原子完成，多个实例不会因为先读后写产生超发。
 * 返回值为非负数时表示剩余配额；负数的绝对值表示被拒绝请求需要等待的毫秒数。</p>
 */
public class RedisDistributedRateLimiter implements DistributedRateLimiter {

    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then
                redis.call('PEXPIRE', KEYS[1], ARGV[2])
                ttl = tonumber(ARGV[2])
            end
            if current > tonumber(ARGV[1]) then
                return -ttl
            end
            return tonumber(ARGV[1]) - current
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final Duration window;

    public RedisDistributedRateLimiter(StringRedisTemplate redisTemplate, RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = requireText(properties.getKeyPrefix(), "限流键前缀不能为空");
        this.window = properties.getWindow();
    }

    @Override
    public RateLimitDecision tryAcquire(RateLimitScope scope, String subject, int limit) {
        String key = keyPrefix + ":" + scope.key() + ":" + sha256(subject);
        long windowMillis = Math.max(1L, window.toMillis());
        Long result = redisTemplate.execute(
                ACQUIRE_SCRIPT,
                List.of(key),
                Integer.toString(limit),
                Long.toString(windowMillis)
        );
        if (result == null) {
            throw new IllegalStateException("Redis 未返回限流执行结果");
        }
        if (result >= 0L) {
            return new RateLimitDecision(true, limit, result, 0L);
        }
        long retryAfterSeconds = Math.max(1L, Math.ceilDiv(-result, 1_000L));
        return new RateLimitDecision(false, limit, 0L, retryAfterSeconds);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(requireText(value, "限流主体不能为空").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 运行环境不支持 SHA-256", exception);
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
