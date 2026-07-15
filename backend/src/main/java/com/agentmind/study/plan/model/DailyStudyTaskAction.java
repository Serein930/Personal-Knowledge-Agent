package com.agentmind.study.plan.model;

/** 学习任务不可变事件类型。 */
public enum DailyStudyTaskAction {
    CREATED,
    COMPLETED,
    SKIPPED,
    RESCHEDULED,
    FEEDBACK_RECORDED,
    COMPENSATED
}
