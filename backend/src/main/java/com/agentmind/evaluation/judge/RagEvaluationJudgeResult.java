package com.agentmind.evaluation.judge;

/** 裁判输出中的两个百分制质量分与可审计证据。 */
public record RagEvaluationJudgeResult(
        double faithfulness,
        double answerRelevance,
        RagEvaluationJudgeEvidence evidence
) {
}
