package com.agentmind.study.plan.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 知识空间每日学习计划。
 */
public record DailyStudyPlan(
        Long id,
        Long ownerUserId,
        Long workspaceId,
        LocalDate planDate,
        int dailyReviewTarget,
        int dueCardSnapshot,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public DailyStudyPlan withId(Long generatedId) {
        return new DailyStudyPlan(
                generatedId, ownerUserId, workspaceId, planDate, dailyReviewTarget,
                dueCardSnapshot, createdAt, updatedAt
        );
    }
}
