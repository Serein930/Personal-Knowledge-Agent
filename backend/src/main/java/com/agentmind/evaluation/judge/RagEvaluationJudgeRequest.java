package com.agentmind.evaluation.judge;

import java.util.List;

/** 模型裁判输入；来源正文只包含本次检索实际返回的证据。 */
public record RagEvaluationJudgeRequest(
        String question,
        String answer,
        String sourceContext,
        List<String> expectedAnswerKeywords
) {
}
