package com.agentmind.study.plan.model.dto;

import com.agentmind.study.plan.model.DailyStudyTaskPriority;
import com.agentmind.study.plan.model.DailyStudyTaskStatus;
import com.agentmind.study.plan.model.DailyStudyTaskType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 每日学习任务、显式状态及实时卡片完成情况。 */
public record DailyStudyTaskResponse(
        Long id,
        DailyStudyTaskType type,
        DailyStudyTaskPriority priority,
        DailyStudyTaskStatus status,
        LocalDate scheduledDate,
        String topic,
        Long sourceDocumentId,
        int targetCardCount,
        long completedCardCount,
        boolean completed,
        String reason,
        List<Long> flashcardIds,
        Integer feedbackScore,
        String feedbackComment,
        OffsetDateTime completedAt,
        OffsetDateTime skippedAt,
        long version,
        OffsetDateTime updatedAt
) {
}
