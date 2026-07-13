package com.agentmind.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.chat.model.RagModelCallObservation;
import com.agentmind.chat.model.RagModelCallStatus;
import com.agentmind.chat.model.dto.RagModelCallObservationResponse;
import com.agentmind.chat.repository.InMemoryRagModelCallObservationRepository;
import com.agentmind.common.response.PageResponse;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class RagModelCallAuditServiceTests {

    private final InMemoryRagModelCallObservationRepository repository =
            new InMemoryRagModelCallObservationRepository();
    private final RagModelCallAuditService service = new RagModelCallAuditService(repository);

    @Test
    void listObservationsShouldFilterWorkspaceAndStatus() {
        repository.save(observation("1", 1L, RagModelCallStatus.SUCCEEDED, "mock-local"));
        repository.save(observation("2", 1L, RagModelCallStatus.FALLBACK, "gpt-test"));
        repository.save(observation("3", 2L, RagModelCallStatus.FALLBACK, "other-workspace-model"));

        PageResponse<RagModelCallObservationResponse> response = service.listObservations(
                1L,
                1,
                20,
                RagModelCallStatus.FALLBACK
        );

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.records()).hasSize(1);
        assertThat(response.records().getFirst().id()).isEqualTo("2");
        assertThat(response.records().getFirst().modelName()).isEqualTo("gpt-test");
        assertThat(response.records().getFirst().status()).isEqualTo(RagModelCallStatus.FALLBACK);
    }

    private RagModelCallObservation observation(
            String id,
            Long workspaceId,
            RagModelCallStatus status,
            String modelName
    ) {
        return new RagModelCallObservation(
                id,
                workspaceId,
                "rag-chat-v1",
                "spring-ai",
                modelName,
                2,
                status == RagModelCallStatus.FALLBACK,
                status,
                15,
                120,
                status == RagModelCallStatus.FALLBACK ? "模型暂不可用" : "",
                OffsetDateTime.now()
        );
    }
}
