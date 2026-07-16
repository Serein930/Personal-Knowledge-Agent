package com.agentmind.evaluation.model;

/** 评估任务最终状态。 */
public enum RagEvaluationJobStatus {
    PENDING,
    RUNNING,
    CANCEL_REQUESTED,
    CANCELED,
    SUCCEEDED,
    FAILED
}
