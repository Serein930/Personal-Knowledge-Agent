package com.agentmind.evaluation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.evaluation.model.RagEvaluationExperimentConfig;
import com.agentmind.evaluation.model.RagEvaluationJob;
import com.agentmind.evaluation.model.RagEvaluationJobStatus;
import com.agentmind.evaluation.model.RagEvaluationRerankStrategy;
import com.agentmind.evaluation.model.RagEvaluationRetrievalStrategy;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 验证内存与 PostgreSQL 适配器必须共同遵守的租约核心语义。 */
class RagEvaluationJobLeaseRepositoryTests {

    private final InMemoryRagEvaluationJobRepository repository = new InMemoryRagEvaluationJobRepository();

    @Test
    void shouldAllowOnlyOneOwnerAndRecoverExpiredRunningLease() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-16T10:00:00+08:00");
        RagEvaluationJob saved = repository.save(pending(now));

        RagEvaluationJob claimed = repository.claim(
                1L, 10L, saved.id(), "instance-a", now, now.plusSeconds(30)
        ).orElseThrow();

        assertThat(repository.claim(
                1L, 10L, saved.id(), "instance-b", now, now.plusSeconds(30)
        )).isEmpty();
        assertThat(repository.renewLease(saved.id(), "instance-b", now, now.plusSeconds(60))).isFalse();
        assertThat(repository.renewLease(saved.id(), "instance-a", now, now.plusSeconds(60))).isTrue();
        assertThat(repository.recoverExpiredLeases(now.plusSeconds(59), 10)).isEmpty();

        List<RagEvaluationJob> recovered = repository.recoverExpiredLeases(now.plusSeconds(61), 10);

        assertThat(recovered).hasSize(1);
        assertThat(recovered.getFirst().status()).isEqualTo(RagEvaluationJobStatus.PENDING);
        assertThat(recovered.getFirst().attemptCount()).isEqualTo(1);
        assertThat(recovered.getFirst().recoveryCount()).isEqualTo(1);
        assertThat(recovered.getFirst().leaseOwner()).isEmpty();
    }

    @Test
    void shouldFinishCancelRequestWhenLeaseOwnerDisappears() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-16T10:00:00+08:00");
        RagEvaluationJob saved = repository.save(pending(now));
        RagEvaluationJob claimed = repository.claim(
                1L, 10L, saved.id(), "instance-a", now, now.plusSeconds(30)
        ).orElseThrow();
        repository.updateIfStatusAndLeaseOwner(
                claimed.withStatus(RagEvaluationJobStatus.CANCEL_REQUESTED, claimed.startedAt(), null, ""),
                Set.of(RagEvaluationJobStatus.RUNNING),
                "instance-a",
                now
        ).orElseThrow();

        RagEvaluationJob recovered = repository.recoverExpiredLeases(now.plusSeconds(31), 10).getFirst();

        assertThat(recovered.status()).isEqualTo(RagEvaluationJobStatus.CANCELED);
        assertThat(recovered.failureReason()).contains("安全取消");
        assertThat(recovered.completedAt()).isNotNull();
    }

    private RagEvaluationJob pending(OffsetDateTime now) {
        RagEvaluationExperimentConfig config = new RagEvaluationExperimentConfig(
                "租约测试", "markdown-aware-v1", RagEvaluationRetrievalStrategy.HYBRID,
                20, RagEvaluationRerankStrategy.NONE, 5, "rag-chat-v1", "mock-local"
        );
        return new RagEvaluationJob(
                null, 1L, 10L, 100L, 1, RagEvaluationJobStatus.PENDING,
                "HYBRID", 5, "rag-chat-v1", "mock-local", config,
                null, null, 2, 0, 0, null, null, null, List.of(), "",
                0, 0, "", null, null, now, null, now, null
        );
    }
}
