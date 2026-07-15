package com.agentmind.evaluation.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 启动一次可重复的固定评估任务。 */
public record StartRagEvaluationJobRequest(
        @NotNull(message = "评估集编号不能为空")
        @Positive(message = "评估集编号必须为正数") Long datasetId,
        @NotNull(message = "评估集版本不能为空")
        @Positive(message = "评估集版本必须为正数") Integer datasetVersion,
        @Min(value = 1, message = "TopK不能小于1")
        @Max(value = 20, message = "TopK不能大于20") Integer topK
) {
}
