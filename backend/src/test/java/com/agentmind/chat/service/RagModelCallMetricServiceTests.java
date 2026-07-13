package com.agentmind.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.chat.model.RagModelCallObservation;
import com.agentmind.chat.model.RagModelCallStatus;
import com.agentmind.chat.model.dto.RagModelCallMetricGroupResponse;
import com.agentmind.chat.model.dto.RagModelCallMetricsResponse;
import com.agentmind.chat.repository.InMemoryRagModelCallObservationRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class RagModelCallMetricServiceTests {

    private final InMemoryRagModelCallObservationRepository repository =
            new InMemoryRagModelCallObservationRepository();
    private final RagModelCallMetricService service = new RagModelCallMetricService(repository);

    @Test
    void getMetricsShouldAggregateByWorkspaceModelAndPromptVersion() {
        repository.save(observation("1", 1L, "mock-local", "rag-chat-v1", RagModelCallStatus.SUCCEEDED, 100));
        repository.save(observation("2", 1L, "mock-local", "rag-chat-v1", RagModelCallStatus.SUCCEEDED, 200));
        repository.save(observation("3", 1L, "gpt-test", "rag-chat-v2", RagModelCallStatus.FALLBACK, 300));
        repository.save(observation("4", 1L, "gpt-test", "rag-chat-v2", RagModelCallStatus.FAILED, 400));
        repository.save(observation("5", 2L, "other-model", "rag-chat-v1", RagModelCallStatus.SUCCEEDED, 50));

        RagModelCallMetricsResponse response = service.getMetrics(1L);

        assertThat(response.workspaceId()).isEqualTo(1L);
        assertThat(response.totalCallCount()).isEqualTo(4);
        assertThat(response.successfulCallCount()).isEqualTo(2);
        assertThat(response.fallbackCallCount()).isEqualTo(1);
        assertThat(response.failedCallCount()).isEqualTo(1);
        assertThat(response.successRate()).isEqualByComparingTo("0.5000");
        assertThat(response.fallbackRate()).isEqualByComparingTo("0.2500");
        assertThat(response.averageElapsedMillis()).isEqualByComparingTo("250.00");
        assertThat(response.groups()).hasSize(2);

        RagModelCallMetricGroupResponse mockGroup = findGroup(response, "mock-local", "rag-chat-v1");
        assertThat(mockGroup.totalCallCount()).isEqualTo(2);
        assertThat(mockGroup.successRate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(mockGroup.fallbackRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(mockGroup.averageElapsedMillis()).isEqualByComparingTo("150.00");

        RagModelCallMetricGroupResponse fallbackGroup = findGroup(response, "gpt-test", "rag-chat-v2");
        assertThat(fallbackGroup.totalCallCount()).isEqualTo(2);
        assertThat(fallbackGroup.successRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(fallbackGroup.fallbackRate()).isEqualByComparingTo("0.5000");
        assertThat(fallbackGroup.averageElapsedMillis()).isEqualByComparingTo("350.00");
    }

    @Test
    void getMetricsShouldReturnZeroValuesWhenWorkspaceHasNoCalls() {
        RagModelCallMetricsResponse response = service.getMetrics(99L);

        assertThat(response.totalCallCount()).isZero();
        assertThat(response.successRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.fallbackRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.averageElapsedMillis()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.groups()).isEmpty();
    }

    private RagModelCallMetricGroupResponse findGroup(
            RagModelCallMetricsResponse response,
            String modelName,
            String promptVersion
    ) {
        return response.groups().stream()
                .filter(group -> group.modelName().equals(modelName))
                .filter(group -> group.promptVersion().equals(promptVersion))
                .findFirst()
                .orElseThrow();
    }

    private RagModelCallObservation observation(
            String id,
            Long workspaceId,
            String modelName,
            String promptVersion,
            RagModelCallStatus status,
            long elapsedMillis
    ) {
        return new RagModelCallObservation(
                id,
                workspaceId,
                promptVersion,
                "spring-ai",
                modelName,
                2,
                status == RagModelCallStatus.FALLBACK,
                status,
                elapsedMillis,
                120,
                status == RagModelCallStatus.SUCCEEDED ? "" : "模型暂不可用",
                OffsetDateTime.now()
        );
    }
}
