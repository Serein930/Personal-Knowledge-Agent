package com.agentmind.evaluation.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

/** 验证生产指标使用固定低基数标签并正确累加。 */
class RagEvaluationObservabilityTests {

    @Test
    void shouldRecordOutcomeAndRecoveryCounters() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RagEvaluationObservability observability = new RagEvaluationObservability(
                ObservationRegistry.NOOP, meterRegistry
        );

        observability.recordJobOutcome("SUCCEEDED");
        observability.recordRecovery("redispatched", 2);

        assertThat(meterRegistry.get("agentmind.rag.evaluation.jobs")
                .tag("outcome", "succeeded").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.get("agentmind.rag.evaluation.lease.recoveries")
                .tag("outcome", "redispatched").counter().count()).isEqualTo(2);
    }
}
