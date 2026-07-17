package com.agentmind.common.ratelimit;

/** 一次原子配额扣减的结果。 */
public record RateLimitDecision(boolean allowed, int limit, long remaining, long retryAfterSeconds) {
}
