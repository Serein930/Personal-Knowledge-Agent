package com.agentmind.evaluation.model.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** 创建评估任务时可选的 CI 质量门禁阈值。 */
public record RagEvaluationQualityGateRequest(
        @DecimalMin(value = "0.0", message = "最低Recall@K不能小于0")
        @DecimalMax(value = "100.0", message = "最低Recall@K不能大于100") Double minimumRecallAtK,
        @DecimalMin(value = "0.0", message = "最低NDCG@K不能小于0")
        @DecimalMax(value = "100.0", message = "最低NDCG@K不能大于100") Double minimumNdcgAtK,
        @DecimalMin(value = "0.0", message = "最低忠实度不能小于0")
        @DecimalMax(value = "100.0", message = "最低忠实度不能大于100") Double minimumFaithfulness,
        @DecimalMin(value = "0.0", message = "最低答案相关性不能小于0")
        @DecimalMax(value = "100.0", message = "最低答案相关性不能大于100") Double minimumAnswerRelevance,
        @Positive(message = "最高平均耗时必须为正数") Long maximumAverageLatencyMillis,
        @Positive(message = "最高Token数量必须为正数") Integer maximumTotalTokens,
        @DecimalMin(value = "0.0", inclusive = false, message = "最高成本必须大于0")
        BigDecimal maximumEstimatedCostUsd
) {
}
