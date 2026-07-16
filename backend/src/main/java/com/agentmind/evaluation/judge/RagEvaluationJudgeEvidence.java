package com.agentmind.evaluation.judge;

/** 单题裁判证据，便于区分确定性近似分与真实模型评分。 */
public record RagEvaluationJudgeEvidence(
        String judgeType,
        String modelName,
        String promptVersion,
        String rationale,
        boolean fallbackUsed
) {
}
