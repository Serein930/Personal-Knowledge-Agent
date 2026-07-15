package com.agentmind.study.plan.model.dto;

import com.agentmind.study.plan.model.DailyStudyTaskPriority;
import com.agentmind.study.plan.model.DailyStudyTaskType;
import java.util.List;

/** 每日学习任务及其实时完成情况。 */
public record DailyStudyTaskResponse(
        Long id,
        DailyStudyTaskType type,
        DailyStudyTaskPriority priority,
        String topic,
        Long sourceDocumentId,
        int targetCardCount,
        long completedCardCount,
        boolean completed,
        String reason,
        List<Long> flashcardIds
) {
}
