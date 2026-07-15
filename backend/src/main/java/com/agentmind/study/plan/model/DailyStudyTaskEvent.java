package com.agentmind.study.plan.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 每日学习任务的一次不可变状态变更证据。 */
public record DailyStudyTaskEvent(
        Long id,
        Long taskId,
        Long ownerUserId,
        Long workspaceId,
        DailyStudyTaskAction action,
        DailyStudyTaskStatus previousStatus,
        DailyStudyTaskStatus nextStatus,
        LocalDate previousScheduledDate,
        LocalDate nextScheduledDate,
        Integer feedbackScore,
        String comment,
        OffsetDateTime createdAt
) {

    public DailyStudyTaskEvent withId(Long generatedId) {
        return new DailyStudyTaskEvent(
                generatedId, taskId, ownerUserId, workspaceId, action,
                previousStatus, nextStatus, previousScheduledDate, nextScheduledDate,
                feedbackScore, comment, createdAt
        );
    }
}
