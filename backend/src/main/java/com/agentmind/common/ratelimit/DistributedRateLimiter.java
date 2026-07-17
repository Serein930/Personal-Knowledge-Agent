package com.agentmind.common.ratelimit;

/** 多实例共享配额的存储端口。 */
public interface DistributedRateLimiter {

    RateLimitDecision tryAcquire(RateLimitScope scope, String subject, int limit);
}
