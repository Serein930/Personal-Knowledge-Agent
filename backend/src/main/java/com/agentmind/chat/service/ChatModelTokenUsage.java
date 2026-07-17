package com.agentmind.chat.service;

import com.agentmind.chat.model.dto.TokenUsageResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * 将 Spring AI 模型用量统一转换为项目内部 Token 契约。
 *
 * <p>同步和流式适配器共用该映射，避免不同入口对空元数据、总 Token 缺失等情况产生不同解释。</p>
 */
final class ChatModelTokenUsage {

    private ChatModelTokenUsage() {
    }

    static TokenUsageResponse from(ChatResponse response) {
        Usage usage = response == null || response.getMetadata() == null
                ? null
                : response.getMetadata().getUsage();
        if (usage == null) {
            return zero();
        }
        int promptTokens = nonNegative(usage.getPromptTokens());
        int completionTokens = nonNegative(usage.getCompletionTokens());
        Integer totalTokens = usage.getTotalTokens();
        return new TokenUsageResponse(
                promptTokens,
                completionTokens,
                totalTokens == null ? promptTokens + completionTokens : Math.max(0, totalTokens)
        );
    }

    static TokenUsageResponse add(TokenUsageResponse left, TokenUsageResponse right) {
        return new TokenUsageResponse(
                left.promptTokens() + right.promptTokens(),
                left.completionTokens() + right.completionTokens(),
                left.totalTokens() + right.totalTokens()
        );
    }

    static TokenUsageResponse zero() {
        return new TokenUsageResponse(0, 0, 0);
    }

    private static int nonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }
}
