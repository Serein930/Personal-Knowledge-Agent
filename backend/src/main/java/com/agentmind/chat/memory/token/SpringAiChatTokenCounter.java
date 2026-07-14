package com.agentmind.chat.memory.token;

import com.agentmind.chat.memory.model.ChatMessageRole;
import org.springframework.ai.tokenizer.TokenCountEstimator;

/**
 * 基于 Spring AI 分词器的会话令牌计算适配器。
 *
 * <p>计算内容包含与提示词一致的中文角色标签和换行符，因此预算不仅覆盖消息正文，
 * 也覆盖短期历史写入提示词时产生的固定格式开销。</p>
 */
public class SpringAiChatTokenCounter implements ChatTokenCounter {

    private final TokenCountEstimator estimator;

    public SpringAiChatTokenCounter(TokenCountEstimator estimator) {
        this.estimator = estimator;
    }

    @Override
    public int countTokens(ChatMessageRole role, String content) {
        String roleLabel = role == ChatMessageRole.USER ? "用户：" : "助手：";
        String normalizedContent = content == null ? "" : content;
        return estimator.estimate(roleLabel + normalizedContent + "\n");
    }
}
