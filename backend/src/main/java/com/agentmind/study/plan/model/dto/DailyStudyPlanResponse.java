package com.agentmind.study.plan.model.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 每日学习计划及其实时完成进度。
 */
public record DailyStudyPlanResponse(
        Long id,
        Long workspaceId,
        LocalDate planDate,
        int dailyReviewTarget,
        int dueCardSnapshot,
        long completedReviews,
        long remainingReviews,
        double progress,
        boolean completed,
        OffsetDateTime updatedAt
) {
}
