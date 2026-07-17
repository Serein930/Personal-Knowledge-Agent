package com.agentmind.common.ratelimit;

/**
 * 接口配额分类。
 *
 * <p>分类保持较粗粒度，避免把原始 URI 或知识空间编号写入指标标签和 Redis 键，
 * 从而控制指标基数并防止配额被路径参数无限拆分。</p>
 */
public enum RateLimitScope {
    AUTHENTICATION("authentication"),
    INGESTION("ingestion"),
    RAG("rag"),
    GENERAL("general");

    private final String key;

    RateLimitScope(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
