package com.agentmind.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 注册 Redis 限流适配器，并确保过滤器只由 Spring Security 过滤链执行一次。 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "agentmind.rate-limit", name = "mode", havingValue = "redis")
    public DistributedRateLimiter distributedRateLimiter(
            StringRedisTemplate redisTemplate,
            RateLimitProperties properties
    ) {
        return new RedisDistributedRateLimiter(redisTemplate, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "agentmind.rate-limit", name = "mode", havingValue = "redis")
    public DistributedRateLimitFilter distributedRateLimitFilter(
            DistributedRateLimiter rateLimiter,
            RateLimitProperties properties,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        return new DistributedRateLimitFilter(rateLimiter, properties, objectMapper, meterRegistry);
    }

    @Bean
    @ConditionalOnProperty(prefix = "agentmind.rate-limit", name = "mode", havingValue = "redis")
    public FilterRegistrationBean<DistributedRateLimitFilter> disableContainerRateLimitFilterRegistration(
            DistributedRateLimitFilter filter
    ) {
        FilterRegistrationBean<DistributedRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
