package com.agentmind.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentmind.chat.config.RagAnswerGenerationProperties;
import com.agentmind.chat.repository.InMemoryRagModelCallObservationRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
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
        assertThat(answer.metadata().refusalReason()).contains("模型不可用");
        assertThat(answer.usage().totalTokens()).isZero();
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
            throw new IllegalStateException("模型不可用");
        }
    }
}
