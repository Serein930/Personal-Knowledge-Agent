package com.agentmind.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 验证 Spring AI 自动配置与项目回答生成适配器能够在同一个应用容器中正确装配。
 *
 * <p>本测试只创建 OpenAI 兼容客户端，不发送任何外部请求，因此不会消耗模型额度。
 * 它重点防止在组件扫描阶段使用 {@code ConditionalOnBean} 导致适配器早于
 * {@code ChatModel} 自动配置被错误排除的回归问题。</p>
 */
@SpringBootTest(properties = {
        "agentmind.rag.answer-generator=spring-ai",
        "spring.ai.model.chat=openai",
        "spring.ai.model.embedding=none",
        "spring.ai.openai.api-key=仅用于容器装配测试的占位密钥"
})
class SpringAiAnswerGeneratorContextTests {

    @Autowired
    private AnswerGenerator answerGenerator;

    @Autowired
    private StreamingAnswerGenerator streamingAnswerGenerator;

    @Test
    void shouldExposeSynchronousAndStreamingSpringAiAdapters() {
        assertThat(answerGenerator).isInstanceOf(SpringAiChatModelAnswerGenerator.class);
        assertThat(streamingAnswerGenerator).isInstanceOf(SpringAiStreamingAnswerGenerator.class);
    }
}
