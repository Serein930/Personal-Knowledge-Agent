package com.agentmind.evaluation.model.dto;

import java.time.OffsetDateTime;

/** 评估集列表响应。 */
public record RagEvaluationDatasetResponse(
        Long id,
        String name,
        String description,
        int latestVersion,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
