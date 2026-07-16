package com.agentmind.evaluation.model;

/** 评估任务候选片段重排策略。 */
public enum RagEvaluationRerankStrategy {
    NONE,
    /** 使用本地确定性词法相似度重排，不依赖付费模型。 */
    LEXICAL
}
