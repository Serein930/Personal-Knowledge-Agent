package com.agentmind.evaluation.metric;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.evaluation.model.RagEvaluationMetrics;
import com.agentmind.evaluation.model.RagEvaluationQualityGate;
import com.agentmind.evaluation.model.RagEvaluationQualityGateResult;
import com.agentmind.evaluation.model.RagEvaluationQualityGateStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 质量门禁下限、上限和无配置状态测试。 */
class RagEvaluationQualityGateEvaluatorTests {

    private final RagEvaluationQualityGateEvaluator evaluator = new RagEvaluationQualityGateEvaluator();

    @Test
    void shouldReportEveryViolatedThresholdWithoutChangingJobStatus() {
        RagEvaluationQualityGate gate = new RagEvaluationQualityGate(
                90.0, 85.0, 80.0, 75.0, 500L, 1000, new BigDecimal("0.010000")
        );
        RagEvaluationMetrics metrics = new RagEvaluationMetrics(
                2, 80, 0.5, 70, 100, 100, 100, 60, 65,
                100, 20, 500, 620, 700, 500, 1200, true, new BigDecimal("0.020000")
        );

        RagEvaluationQualityGateResult result = evaluator.evaluate(gate, metrics);

        assertThat(result.status()).isEqualTo(RagEvaluationQualityGateStatus.FAILED);
        assertThat(result.violations()).hasSize(7);
    }

    @Test
    void shouldReturnNotConfiguredWhenNoGateExists() {
        RagEvaluationQualityGateResult result = evaluator.evaluate(null, null);
        assertThat(result.status()).isEqualTo(RagEvaluationQualityGateStatus.NOT_CONFIGURED);
        assertThat(result.violations()).isEmpty();
    }
}
