package com.agentmind.evaluation.model;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 评估任务及其完整运行快照。
 *
 * <p>任务保存数据集版本、TopK、提示词版本、模型名称、基线任务和逐题结果，
 * 因此指标变化能够追溯到具体配置和具体题目。</p>
 */
public record RagEvaluationJob(
        Long id,
        Long ownerUserId,
        Long workspaceId,
        Long datasetId,
        int datasetVersion,
        RagEvaluationJobStatus status,
        String retrievalStrategy,
        int topK,
        String promptVersion,
        String modelName,
        Long baselineJobId,
        RagEvaluationMetrics metrics,
        List<RagEvaluationCaseResult> caseResults,
        String failureReason,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {
}
