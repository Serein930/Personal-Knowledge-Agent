package com.agentmind.study.plan.model.dto;

import com.agentmind.study.plan.model.DailyStudyTaskAction;
import com.agentmind.study.plan.model.DailyStudyTaskStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 学习任务事件响应。 */
public record DailyStudyTaskEventResponse(
        Long id,
        DailyStudyTaskAction action,
        DailyStudyTaskStatus previousStatus,
        DailyStudyTaskStatus nextStatus,
        LocalDate previousScheduledDate,
        LocalDate nextScheduledDate,
        Integer feedbackScore,
        String comment,
        OffsetDateTime createdAt
) {
}
