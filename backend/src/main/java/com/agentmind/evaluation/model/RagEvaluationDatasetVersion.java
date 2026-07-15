package com.agentmind.evaluation.model;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 不可变评估集版本。
 *
 * <p>版本一经创建不允许修改。新增或修正题目必须创建下一版本，保证历史任务始终能找到原始输入。</p>
 */
public record RagEvaluationDatasetVersion(
        Long id,
        Long datasetId,
        Long ownerUserId,
        Long workspaceId,
        int version,
        String changeNote,
        List<RagEvaluationCase> cases,
        OffsetDateTime createdAt
) {
}
