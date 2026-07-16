package com.agentmind.evaluation.model.dto;

import java.util.List;

/** 指定评估集的时间序列指标。数据点按完成时间升序返回。 */
public record RagEvaluationTrendResponse(
        Long datasetId,
        Integer datasetVersion,
        List<RagEvaluationTrendPointResponse> points
) {
}
