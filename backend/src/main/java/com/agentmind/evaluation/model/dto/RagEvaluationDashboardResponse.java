package com.agentmind.evaluation.model.dto;

import java.util.List;

/** 前端评估面板一次加载所需的数据。 */
public record RagEvaluationDashboardResponse(
        long datasetCount,
        long totalJobCount,
        long successfulJobCount,
        RagEvaluationJobResponse latestSuccessfulJob,
        RagEvaluationComparisonResponse latestComparison,
        List<RagEvaluationJobResponse> recentJobs
) {
}
