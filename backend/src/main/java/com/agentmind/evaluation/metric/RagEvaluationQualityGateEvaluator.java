package com.agentmind.evaluation.metric;

import com.agentmind.evaluation.model.RagEvaluationMetrics;
import com.agentmind.evaluation.model.RagEvaluationQualityGate;
import com.agentmind.evaluation.model.RagEvaluationQualityGateResult;
import com.agentmind.evaluation.model.RagEvaluationQualityGateStatus;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** 将评估聚合指标与任务冻结的质量阈值进行确定性比较。 */
@Component
public class RagEvaluationQualityGateEvaluator {

    public RagEvaluationQualityGateResult evaluate(RagEvaluationQualityGate gate, RagEvaluationMetrics metrics) {
        if (gate == null || !gate.configured()) {
            return new RagEvaluationQualityGateResult(RagEvaluationQualityGateStatus.NOT_CONFIGURED, List.of());
        }
        List<String> violations = new ArrayList<>();
        minimum("Recall@K", metrics.recallAtK(), gate.minimumRecallAtK(), violations);
        minimum("NDCG@K", metrics.ndcgAtK(), gate.minimumNdcgAtK(), violations);
        minimum("忠实度", metrics.faithfulness(), gate.minimumFaithfulness(), violations);
        minimum("答案相关性", metrics.answerRelevance(), gate.minimumAnswerRelevance(), violations);
        if (gate.maximumAverageLatencyMillis() != null
                && metrics.averageLatencyMillis() > gate.maximumAverageLatencyMillis()) {
            violations.add("平均耗时 " + metrics.averageLatencyMillis() + "ms 超过上限 "
                    + gate.maximumAverageLatencyMillis() + "ms");
        }
        if (gate.maximumTotalTokens() != null && metrics.totalTokens() > gate.maximumTotalTokens()) {
            violations.add("Token总量 " + metrics.totalTokens() + " 超过上限 " + gate.maximumTotalTokens());
        }
        if (gate.maximumEstimatedCostUsd() != null
                && metrics.estimatedCostUsd().compareTo(gate.maximumEstimatedCostUsd()) > 0) {
            violations.add("估算成本 " + metrics.estimatedCostUsd() + " 美元超过上限 "
                    + gate.maximumEstimatedCostUsd() + " 美元");
        }
        return new RagEvaluationQualityGateResult(
                violations.isEmpty() ? RagEvaluationQualityGateStatus.PASSED : RagEvaluationQualityGateStatus.FAILED,
                List.copyOf(violations)
        );
    }

    private void minimum(String name, double actual, Double minimum, List<String> violations) {
        if (minimum != null && actual < minimum) {
            violations.add(name + " " + actual + " 低于下限 " + minimum);
        }
    }
}
