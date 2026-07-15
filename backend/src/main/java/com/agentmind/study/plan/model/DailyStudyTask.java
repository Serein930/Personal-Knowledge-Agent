package com.agentmind.study.plan.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 每日学习计划中的一项可执行任务。
 *
 * <p>任务保存生成时的卡片集合及独立排期。状态使用版本号保护，改期不会改变原计划日期，
 * 因而历史计划和任务实际执行日期可以同时被解释。</p>
 */
public record DailyStudyTask(
        Long id,
        Long planId,
        Long ownerUserId,
        Long workspaceId,
        DailyStudyTaskType type,
        DailyStudyTaskPriority priority,
        DailyStudyTaskStatus status,
        LocalDate scheduledDate,
        String topic,
        Long sourceDocumentId,
        int targetCardCount,
        String reason,
        List<Long> flashcardIds,
        Integer feedbackScore,
        String feedbackComment,
        OffsetDateTime completedAt,
        OffsetDateTime skippedAt,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public DailyStudyTask {
        flashcardIds = List.copyOf(flashcardIds);
    }

    public DailyStudyTask withIdentity(Long generatedId, Long generatedPlanId) {
        return new DailyStudyTask(
                generatedId, generatedPlanId, ownerUserId, workspaceId, type, priority,
                status, scheduledDate, topic, sourceDocumentId, targetCardCount, reason,
                flashcardIds, feedbackScore, feedbackComment, completedAt, skippedAt,
                version, createdAt, updatedAt
        );
    }
}
