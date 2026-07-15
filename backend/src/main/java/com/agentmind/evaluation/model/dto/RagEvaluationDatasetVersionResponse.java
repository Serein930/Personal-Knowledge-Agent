package com.agentmind.evaluation.model.dto;

import com.agentmind.evaluation.model.RagEvaluationCase;
import java.time.OffsetDateTime;
import java.util.List;

/** 不可变评估集版本详情。 */
public record RagEvaluationDatasetVersionResponse(
        Long id,
        Long datasetId,
        int version,
        String changeNote,
        List<RagEvaluationCase> cases,
        OffsetDateTime createdAt
) {
}
