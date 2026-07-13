package com.agentmind.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentmind.chat.config.RagAnswerGenerationProperties;
import com.agentmind.chat.model.RagModelCallStatus;
import com.agentmind.chat.repository.InMemoryRagModelCallObservationRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class SpringAiStreamingAnswerGeneratorTests {

    private final RagAnswerGenerationProperties properties = new RagAnswerGenerationProperties();
    private final InMemoryRagModelCallObservationRepository repository =
            new InMemoryRagModelCallObservationRepository();
    private final SpringAiStreamingAnswerGenerator generator = new SpringAiStreamingAnswerGenerator(
            new FailingStreamingChatModel(),
            properties,
            new RagModelCallLogger(repository)
    );

    @Test
    void generateShouldStreamFallbackAndSaveOneFallbackObservationWhenModelFailsBeforeFirstDelta() {
        properties.setModelName("openai-stream-test");
        List<String> deltas = new ArrayList<>();

        StreamingGeneratedAnswer answer = generator.generate(request(), deltas::add, () -> { });

        assertThat(String.join("", deltas)).contains("真实聊天模型流式调用失败");
        assertThat(answer.metadata().refused()).isTrue();
        assertThat(repository.countByWorkspaceId(1L, null)).isEqualTo(1);
        assertThat(repository.findByWorkspaceId(1L, null, 0, 10).getFirst().status())
                .isEqualTo(RagModelCallStatus.FALLBACK);
    }

    @Test
    void generateShouldThrowAndSaveOneFailureObservationWhenFallbackIsDisabled() {
        properties.setSpringAiFailureFallbackEnabled(false);

        assertThatThrownBy(() -> generator.generate(request(), ignored -> { }, () -> { }))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("流式模型不可用");

        assertThat(repository.countByWorkspaceId(1L, null)).isEqualTo(1);
        assertThat(repository.findByWorkspaceId(1L, null, 0, 10).getFirst().status())
                .isEqualTo(RagModelCallStatus.FAILED);
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
     * 固定返回错误流，用于验证模型尚未产生文本时的降级和失败审计。
     */
    private static class FailingStreamingChatModel implements StreamingChatModel {

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.error(new IllegalStateException("流式模型不可用"));
        }
    }
}
