package com.agentmind.evaluation.model;

/** 评估任务可重复选择的检索策略。 */
public enum RagEvaluationRetrievalStrategy {
    /** 保持向量库原始相似度顺序。 */
    VECTOR,
    /** 在向量候选集上融合问题与片段的确定性词法相关度。 */
    HYBRID
}
