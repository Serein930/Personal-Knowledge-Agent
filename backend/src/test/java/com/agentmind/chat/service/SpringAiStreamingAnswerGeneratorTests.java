package com.agentmind.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentmind.chat.config.RagAnswerGenerationProperties;
import com.agentmind.chat.model.RagModelCallStatus;
import com.agentmind.chat.repository.InMemoryRagModelCallObservationRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.model.ChatModel;
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
        assertThat(String.join("", deltas)).doesNotContain("供应商内部细节");
        assertThat(answer.metadata().refusalReason()).doesNotContain("供应商内部细节");
        assertThat(answer.metadata().refused()).isTrue();
        assertThat(repository.countByWorkspaceId(1L, null)).isEqualTo(1);
        assertThat(repository.findByWorkspaceId(1L, null, 0, 10).getFirst().status())
                .isEqualTo(RagModelCallStatus.FALLBACK);
    }

    @Test
    void generateShouldReturnLatestStreamTokenUsage() {
        RagAnswerGenerationProperties successProperties = new RagAnswerGenerationProperties();
        successProperties.setToolCallingEnabled(false);
        SpringAiStreamingAnswerGenerator successGenerator = new SpringAiStreamingAnswerGenerator(
                new SuccessfulStreamingChatModel(),
                successProperties,
                new RagModelCallLogger(new InMemoryRagModelCallObservationRepository())
        );
        List<String> deltas = new ArrayList<>();

        StreamingGeneratedAnswer answer = successGenerator.generate(request(), deltas::add, () -> { });

        assertThat(String.join("", deltas)).isEqualTo("真实回答");
        assertThat(answer.usage().promptTokens()).isEqualTo(13);
        assertThat(answer.usage().completionTokens()).isEqualTo(4);
        assertThat(answer.usage().totalTokens()).isEqualTo(17);
    }

    @Test
    void generateShouldIgnoreMetadataEventWhoseTextIsNull() {
        RagAnswerGenerationProperties successProperties = new RagAnswerGenerationProperties();
        successProperties.setToolCallingEnabled(false);
        SpringAiStreamingAnswerGenerator successGenerator = new SpringAiStreamingAnswerGenerator(
                new NullTextThenContentStreamingChatModel(),
                successProperties,
                new RagModelCallLogger(new InMemoryRagModelCallObservationRepository())
        );
        List<String> deltas = new ArrayList<>();

        StreamingGeneratedAnswer answer = successGenerator.generate(request(), deltas::add, () -> { });

        assertThat(String.join("", deltas)).isEqualTo("空事件后的正常回答");
        assertThat(answer.metadata().refused()).isFalse();
    }

    @Test
    void generateShouldUseSynchronousModelWhenProviderStreamFailsBeforeContent() {
        RagAnswerGenerationProperties successProperties = new RagAnswerGenerationProperties();
        successProperties.setToolCallingEnabled(false);
        SpringAiStreamingAnswerGenerator compatibleGenerator = new SpringAiStreamingAnswerGenerator(
                new FailingStreamingChatModel(),
                new SuccessfulSynchronousChatModel(),
                successProperties,
                new RagModelCallLogger(new InMemoryRagModelCallObservationRepository())
        );
        List<String> deltas = new ArrayList<>();

        StreamingGeneratedAnswer answer = compatibleGenerator.generate(request(), deltas::add, () -> { });

        assertThat(String.join("", deltas)).isEqualTo("同步兼容回答");
        assertThat(answer.metadata().refused()).isFalse();
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
            return Flux.error(new IllegalStateException("流式模型不可用：供应商内部细节"));
        }
    }

    private static class SuccessfulSynchronousChatModel implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage("同步兼容回答"))));
        }
    }

    /** 固定返回两个文本增量，并在最后一个响应携带供应商 Token 用量。 */
    private static class SuccessfulStreamingChatModel implements StreamingChatModel {

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            ChatResponse first = new ChatResponse(
                    List.of(new Generation(new AssistantMessage("真实")))
            );
            ChatResponse last = new ChatResponse(
                    List.of(new Generation(new AssistantMessage("回答"))),
                    ChatResponseMetadata.builder()
                            .model("openai-stream-test")
                            .usage(new DefaultUsage(13, 4))
                            .build()
            );
            return Flux.just(first, last);
        }
    }

    /** 模拟兼容服务先返回无文本元数据事件，再返回真实正文。 */
    private static class NullTextThenContentStreamingChatModel implements StreamingChatModel {

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            AssistantMessage emptyOutput = mock(AssistantMessage.class);
            when(emptyOutput.getText()).thenReturn(null);
            ChatResponse metadataOnly = new ChatResponse(List.of(new Generation(emptyOutput)));
            ChatResponse content = new ChatResponse(
                    List.of(new Generation(new AssistantMessage("空事件后的正常回答")))
            );
            return Flux.just(metadataOnly, content);
        }
    }
}
