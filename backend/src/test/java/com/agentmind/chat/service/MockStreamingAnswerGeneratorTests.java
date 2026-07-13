package com.agentmind.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentmind.chat.config.RagAnswerGenerationProperties;
import com.agentmind.chat.model.RagModelCallStatus;
import com.agentmind.chat.repository.InMemoryRagModelCallObservationRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MockStreamingAnswerGeneratorTests {

    private final RagAnswerGenerationProperties properties = new RagAnswerGenerationProperties();
    private final InMemoryRagModelCallObservationRepository repository =
            new InMemoryRagModelCallObservationRepository();
    private final MockStreamingAnswerGenerator generator = new MockStreamingAnswerGenerator(
            properties,
            new RagModelCallLogger(repository),
            new MockAnswerComposer()
    );

    @Test
    void generateShouldEmitDeterministicChunksAndSaveOneSuccessObservation() {
        properties.setStreamChunkSize(8);
        List<String> deltas = new ArrayList<>();

        StreamingGeneratedAnswer answer = generator.generate(request(), deltas::add, () -> { });

        assertThat(deltas).hasSizeGreaterThan(1);
        assertThat(String.join("", deltas)).contains("根据当前知识库检索结果");
        assertThat(answer.answerLength()).isEqualTo(String.join("", deltas).length());
        assertThat(answer.metadata().answerGenerator()).isEqualTo("mock-stream");
        assertThat(repository.countByWorkspaceId(1L, null)).isEqualTo(1);
        assertThat(repository.findByWorkspaceId(1L, null, 0, 10).getFirst().status())
                .isEqualTo(RagModelCallStatus.SUCCEEDED);
    }

    @Test
    void generateShouldSaveOneCancelledObservationWhenClientDisconnects() {
        properties.setStreamChunkSize(8);
        AtomicInteger emittedCount = new AtomicInteger();

        assertThatThrownBy(() -> generator.generate(
                request(),
                ignored -> emittedCount.incrementAndGet(),
                () -> {
                    if (emittedCount.get() > 0) {
                        throw new RagStreamTerminatedException(RagStreamTerminationReason.CLIENT_DISCONNECTED);
                    }
                }
        )).isInstanceOf(RagStreamTerminatedException.class);

        assertThat(repository.countByWorkspaceId(1L, null)).isEqualTo(1);
        assertThat(repository.findByWorkspaceId(1L, null, 0, 10).getFirst().status())
                .isEqualTo(RagModelCallStatus.CANCELLED);
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
}
