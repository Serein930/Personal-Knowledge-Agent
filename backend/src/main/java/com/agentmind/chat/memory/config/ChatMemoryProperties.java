package com.agentmind.chat.memory.config;

import java.time.Duration;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 短期会话记忆配置。
 *
 * <p>模型上下文上限减去预留令牌后得到短期历史预算。预留部分用于系统提示词、当前问题、
 * 检索片段和模型输出，避免历史消息占满整个模型上下文。</p>
 */
@Component
@Validated
@ConfigurationProperties(prefix = "agentmind.chat.memory")
public class ChatMemoryProperties {

    private String store = "memory";

    @Min(value = 1, message = "最大历史轮次数必须大于零")
    private int maxHistoryTurns = 12;

    @Min(value = 2, message = "历史消息扫描数量至少为二")
    private int historyScanMessageLimit = 200;

    @Min(value = 128, message = "模型上下文上限不能小于 128 个令牌")
    private int modelContextWindowTokens = 8_192;

    @Min(value = 0, message = "预留令牌数不能为负数")
    private int reservedContextTokens = 4_096;

    private String keyPrefix = "agentmind:chat-memory";
    private int serializationVersion = 1;
    private Duration ttl = Duration.ofDays(7);

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public int getMaxHistoryTurns() {
        return maxHistoryTurns;
    }

    public void setMaxHistoryTurns(int maxHistoryTurns) {
        this.maxHistoryTurns = maxHistoryTurns;
    }

    public int getHistoryScanMessageLimit() {
        return historyScanMessageLimit;
    }

    public void setHistoryScanMessageLimit(int historyScanMessageLimit) {
        this.historyScanMessageLimit = historyScanMessageLimit;
    }

    public int getModelContextWindowTokens() {
        return modelContextWindowTokens;
    }

    public void setModelContextWindowTokens(int modelContextWindowTokens) {
        this.modelContextWindowTokens = modelContextWindowTokens;
    }

    public int getReservedContextTokens() {
        return reservedContextTokens;
    }

    public void setReservedContextTokens(int reservedContextTokens) {
        this.reservedContextTokens = reservedContextTokens;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public int getSerializationVersion() {
        return serializationVersion;
    }

    public void setSerializationVersion(int serializationVersion) {
        this.serializationVersion = serializationVersion;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    @AssertTrue(message = "预留令牌数必须小于模型上下文上限")
    public boolean isTokenBudgetValid() {
        return reservedContextTokens < modelContextWindowTokens;
    }
}
