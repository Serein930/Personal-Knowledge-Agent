package com.agentmind.evaluation.service;

import com.agentmind.evaluation.model.RagEvaluationPhaseTiming;
import com.agentmind.evaluation.model.RagEvaluationRetrievedSource;
import java.math.BigDecimal;
import java.util.List;

/** 单题执行器输出，包含回答、来源、阶段耗时和模型用量，尚未与期望答案计算指标。 */
public record RagEvaluationProbeResult(
        List<RagEvaluationRetrievedSource> retrievedSources,
        boolean refused,
        String answer,
        RagEvaluationPhaseTiming phaseTiming,
        int promptTokens,
        int completionTokens,
        boolean tokenUsageEstimated,
        BigDecimal estimatedCostUsd
) {
}
