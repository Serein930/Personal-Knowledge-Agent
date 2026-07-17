package com.agentmind.knowledge.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentmind.knowledge.vector.config.EmbeddingProperties;
import com.agentmind.knowledge.vector.observability.EmbeddingObservability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;

class SpringAiEmbeddingClientTests {

    private EmbeddingModel embeddingModel;
    private EmbeddingProperties properties;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        embeddingModel = mock(EmbeddingModel.class);
        properties = new EmbeddingProperties();
        properties.setModelName("test-embedding-model");
        properties.setDimensions(3);
        properties.setBatchSize(2);
        properties.setMaximumAttempts(3);
        properties.setRetryInitialBackoff(Duration.ZERO);
        properties.setInputCostPerMillionTokens(new BigDecimal("0.02"));
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    void shouldSplitInputsIntoBatchesAndPreserveOrder() {
        AtomicInteger sequence = new AtomicInteger();
        when(embeddingModel.embedForResponse(anyList())).thenAnswer(invocation -> {
            List<String> batch = invocation.getArgument(0);
            List<float[]> vectors = batch.stream()
                    .map(ignored -> new float[]{sequence.incrementAndGet(), 0, 0})
                    .toList();
            return response(vectors, batch.size() * 4);
        });
        SpringAiEmbeddingClient client = client();

        List<float[]> result = client.embedAll(List.of("一", "二", "三", "四", "五"));

        assertThat(result).hasSize(5);
        assertThat(result).extracting(vector -> vector[0]).containsExactly(1F, 2F, 3F, 4F, 5F);
        verify(embeddingModel, org.mockito.Mockito.times(3)).embedForResponse(anyList());
        assertThat(meterRegistry.get("agentmind.embedding.calls").counter().count()).isEqualTo(3D);
        assertThat(meterRegistry.get("agentmind.embedding.inputs").counter().count()).isEqualTo(5D);
        assertThat(meterRegistry.get("agentmind.embedding.tokens").counter().count()).isEqualTo(20D);
    }

    @Test
    void shouldRetryTransientFailuresWithinConfiguredLimit() {
        when(embeddingModel.embedForResponse(anyList()))
                .thenThrow(new IllegalStateException("临时限流"))
                .thenThrow(new IllegalStateException("临时网络错误"))
                .thenReturn(response(List.of(new float[]{1, 2, 3}), 6));
        SpringAiEmbeddingClient client = client();

        assertThat(client.embed("需要重试的文本")).containsExactly(1F, 2F, 3F);

        verify(embeddingModel, org.mockito.Mockito.times(3)).embedForResponse(anyList());
        assertThat(meterRegistry.get("agentmind.embedding.attempts")
                .tag("outcome", "success").summary().max()).isEqualTo(3D);
    }

    @Test
    void shouldRejectUnexpectedDimensionsWithoutRetrying() {
        when(embeddingModel.embedForResponse(anyList()))
                .thenReturn(response(List.of(new float[]{1, 2}), 3));
        SpringAiEmbeddingClient client = client();

        assertThatThrownBy(() -> client.embed("维度错误"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已尝试 1 次")
                .hasRootCauseMessage("向量模型返回维度必须为 3，实际为 2");

        verify(embeddingModel).embedForResponse(anyList());
        assertThat(meterRegistry.get("agentmind.embedding.calls")
                .tag("outcome", "failure").counter().count()).isEqualTo(1D);
    }

    private SpringAiEmbeddingClient client() {
        return new SpringAiEmbeddingClient(
                embeddingModel,
                properties,
                new EmbeddingObservability(meterRegistry)
        );
    }

    private EmbeddingResponse response(List<float[]> vectors, int inputTokens) {
        List<Embedding> embeddings = java.util.stream.IntStream.range(0, vectors.size())
                .mapToObj(index -> new Embedding(vectors.get(index), index))
                .toList();
        return new EmbeddingResponse(
                embeddings,
                new EmbeddingResponseMetadata(
                        properties.getModelName(),
                        new DefaultUsage(inputTokens, 0)
                )
        );
    }
}
