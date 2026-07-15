package com.agentmind.evaluation.metric;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.evaluation.model.RagEvaluationCase;
import com.agentmind.evaluation.model.RagEvaluationCaseResult;
import com.agentmind.evaluation.model.RagEvaluationMetrics;
import com.agentmind.evaluation.model.RagEvaluationRetrievedSource;
import com.agentmind.evaluation.service.RagEvaluationProbeResult;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 固定评估指标的分母、排名和成本聚合测试。 */
class RagEvaluationMetricCalculatorTests {

    private final RagEvaluationMetricCalculator calculator = new RagEvaluationMetricCalculator();

    @Test
    void shouldCalculateRetrievalCitationRefusalAndCostWithIndependentDenominators() {
        RagEvaluationCase answerable = new RagEvaluationCase(
                "answerable", "什么是虚拟线程？", List.of("chunk-a", "chunk-b"), List.of(),
                false, List.of("虚拟线程", "阻塞")
        );
        RagEvaluationProbeResult answerableProbe = new RagEvaluationProbeResult(
                List.of(
                        new RagEvaluationRetrievedSource("noise", 9L, 1, 0.9),
                        new RagEvaluationRetrievedSource("chunk-a", 10L, 2, 0.8)
                ), false, "虚拟线程适合处理大量阻塞任务。", 120, 100, 20, true,
                new BigDecimal("0.001000")
        );
        RagEvaluationCase refusal = new RagEvaluationCase(
                "refusal", "资料外问题", List.of(), List.of(), true, List.of()
        );
        RagEvaluationProbeResult refusalProbe = new RagEvaluationProbeResult(
                List.of(), true, "资料不足。", 80, 30, 10, true, new BigDecimal("0.000200")
        );

        RagEvaluationCaseResult answerableResult = calculator.calculateCase(answerable, answerableProbe);
        RagEvaluationCaseResult refusalResult = calculator.calculateCase(refusal, refusalProbe);
        RagEvaluationMetrics metrics = calculator.aggregate(List.of(answerableResult, refusalResult));

        assertThat(answerableResult.recallAtK()).isEqualTo(50.0);
        assertThat(answerableResult.reciprocalRank()).isEqualTo(0.5);
        assertThat(answerableResult.citationCovered()).isTrue();
        assertThat(metrics.recallAtK()).isEqualTo(50.0);
        assertThat(metrics.meanReciprocalRank()).isEqualTo(0.5);
        assertThat(metrics.citationCoverage()).isEqualTo(100.0);
        assertThat(metrics.refusalAccuracy()).isEqualTo(100.0);
        assertThat(metrics.answerKeywordCoverage()).isEqualTo(100.0);
        assertThat(metrics.averageLatencyMillis()).isEqualTo(100);
        assertThat(metrics.totalTokens()).isEqualTo(160);
        assertThat(metrics.estimatedCostUsd()).isEqualByComparingTo("0.001200");
    }
}
