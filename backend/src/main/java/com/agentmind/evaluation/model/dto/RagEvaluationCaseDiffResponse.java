package com.agentmind.evaluation.model.dto;

import com.agentmind.evaluation.model.RagEvaluationCase;
import com.agentmind.evaluation.model.RagEvaluationCaseDiffType;

/** 通过稳定题目标识对齐后的单题版本差异。 */
public record RagEvaluationCaseDiffResponse(
        String caseKey,
        RagEvaluationCaseDiffType type,
        RagEvaluationCase before,
        RagEvaluationCase after
) {
}
