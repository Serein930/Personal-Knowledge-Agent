package com.agentmind.evaluation.judge;

/** 忠实度与答案相关性裁判端口。 */
public interface RagEvaluationAnswerJudge {

    RagEvaluationJudgeResult judge(RagEvaluationJudgeRequest request);
}
