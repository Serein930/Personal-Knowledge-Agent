package com.agentmind.evaluation.model;

import java.time.OffsetDateTime;

/** 固定评估集主记录，具体题目保存在不可变版本中。 */
public record RagEvaluationDataset(
        Long id,
        Long ownerUserId,
        Long workspaceId,
        String name,
        String description,
        int latestVersion,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
