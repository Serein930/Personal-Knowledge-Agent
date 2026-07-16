package com.agentmind.evaluation.model.dto;

import java.util.List;

/** 两个不可变评估集版本的逐题差异。 */
public record RagEvaluationVersionDiffResponse(
        Long datasetId,
        int fromVersion,
        int toVersion,
        int added,
        int removed,
        int modified,
        int unchanged,
        List<RagEvaluationCaseDiffResponse> cases
) {
}
