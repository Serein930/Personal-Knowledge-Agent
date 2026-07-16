package com.agentmind.evaluation.model;

import java.util.List;

/** 质量门禁判定结果和可直接用于 CI 失败说明的违规列表。 */
public record RagEvaluationQualityGateResult(
        RagEvaluationQualityGateStatus status,
        List<String> violations
) {
}
