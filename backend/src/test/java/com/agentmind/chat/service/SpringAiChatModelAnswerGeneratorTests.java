package com.agentmind.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentmind.chat.config.RagAnswerGenerationProperties;
import com.agentmind.chat.repository.InMemoryRagModelCallObservationRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class SpringAiChatModelAnswerGeneratorTests {

    private final RagAnswerGenerationProperties properties = new RagAnswerGenerationProperties();
    private final RagModelCallLogger modelCallLogger = new RagModelCallLogger(
            new InMemoryRagModelCallObservationRepository()
    );

    @Test
    void generateShouldReturnFallbackAnswerWhenRealModelFailsAndFallbackIsEnabled() {
        properties.setModelName("openai-local");
        SpringAiChatModelAnswerGenerator generator = new SpringAiChatModelAnswerGenerator(
                new FailingChatModel(),
                properties,
                modelCallLogger
        );

        GeneratedAnswer answer = generator.generate(request());

        assertThat(answer.content()).contains("真实聊天模型调用失败");
        assertThat(answer.metadata().answerGenerator()).isEqualTo("spring-ai");
        assertThat(answer.metadata().modelName()).isEqualTo("openai-local");
        assertThat(answer.metadata().refused()).isTrue();
        assertThat(answer.metadata().refusalReason()).contains("安全降级");
        assertThat(answer.content()).doesNotContain("供应商内部细节");
        assertThat(answer.metadata().refusalReason()).doesNotContain("供应商内部细节");
        assertThat(answer.usage().totalTokens()).isZero();
    }

    @Test
    void generateShouldReturnContentAndRealTokenUsage() {
        properties.setModelName("openai-local");
        properties.setToolCallingEnabled(false);
        SpringAiChatModelAnswerGenerator generator = new SpringAiChatModelAnswerGenerator(
                new SuccessfulChatModel(),
                properties,
                modelCallLogger
        );

        GeneratedAnswer answer = generator.generate(request());

        assertThat(answer.content()).isEqualTo("线程池复用工作线程并限制并发资源。");
        assertThat(answer.usage().promptTokens()).isEqualTo(11);
        assertThat(answer.usage().completionTokens()).isEqualTo(5);
        assertThat(answer.usage().totalTokens()).isEqualTo(16);
    }

    @Test
    void generateShouldThrowOriginalExceptionWhenFallbackIsDisabled() {
        properties.setSpringAiFailureFallbackEnabled(false);
        SpringAiChatModelAnswerGenerator generator = new SpringAiChatModelAnswerGenerator(
                new FailingChatModel(),
                properties,
                modelCallLogger
        );

        assertThatThrownBy(() -> generator.generate(request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型不可用");
    }

    private AnswerGenerationRequest request() {
        return new AnswerGenerationRequest(
                1L,
                "请解释线程池的作用",
                "rag-chat-v1",
                "检索上下文",
                "模型提示词",
                List.of(),
                new RagRefusalDecision(false, "")
        );
    }

    /**
     * 总是抛出异常的聊天模型，用于模拟真实供应商超时、密钥错误或网络故障。
     */
    private static class FailingChatModel implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            throw new IllegalStateException("模型不可用：供应商内部细节");
        }
    }

    /** 固定返回模型内容和用量，验证真实响应不会丢失 Token 元数据。 */
    private static class SuccessfulChatModel implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(
                    List.of(new Generation(new AssistantMessage("线程池复用工作线程并限制并发资源。"))),
                    ChatResponseMetadata.builder()
                            .model("openai-test")
                            .usage(new DefaultUsage(11, 5))
                            .build()
            );
        }
    }
}
