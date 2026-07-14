package com.agentmind.chat.memory.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.chat.memory.model.ChatMessageRole;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;

/**
 * Spring AI 会话令牌计算适配器测试。
 */
class SpringAiChatTokenCounterTests {

    private final ChatTokenCounter tokenCounter = new SpringAiChatTokenCounter(
            new JTokkitTokenCountEstimator()
    );

    @Test
    void longerChineseMessageShouldConsumeMoreTokens() {
        int shortMessageTokens = tokenCounter.countTokens(ChatMessageRole.USER, "你好");
        int longMessageTokens = tokenCounter.countTokens(
                ChatMessageRole.USER,
                "请结合知识库详细解释线程池复用工作线程的实现机制"
        );

        assertThat(shortMessageTokens).isPositive();
        assertThat(longMessageTokens).isGreaterThan(shortMessageTokens);
    }
}
