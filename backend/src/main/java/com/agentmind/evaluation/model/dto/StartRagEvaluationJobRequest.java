package com.agentmind.evaluation.model.dto;

import com.agentmind.evaluation.model.RagEvaluationRerankStrategy;
import com.agentmind.evaluation.model.RagEvaluationRetrievalStrategy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 启动一次可重复的固定评估任务。 */
public record StartRagEvaluationJobRequest(
        @NotNull(message = "评估集编号不能为空")
        @Positive(message = "评估集编号必须为正数") Long datasetId,
        @NotNull(message = "评估集版本不能为空")
        @Positive(message = "评估集版本必须为正数") Integer datasetVersion,
        @Min(value = 1, message = "TopK不能小于1")
        @Max(value = 20, message = "TopK不能大于20") Integer topK,
        @Size(max = 120, message = "实验名称不能超过120个字符") String experimentName,
        RagEvaluationRetrievalStrategy retrievalStrategy,
        @Min(value = 1, message = "候选池不能小于1")
        @Max(value = 100, message = "候选池不能大于100") Integer candidatePoolSize,
        RagEvaluationRerankStrategy rerankStrategy,
        @Valid RagEvaluationQualityGateRequest qualityGate
) {
}
