package com.agentmind.study.flashcard.model.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 当前知识空间的复习工作台统计。
 */
public record StudyReviewStatisticsResponse(
        long dueCount,
        long completedToday,
        double accuracyToday,
        int currentStreakDays,
        long totalReviews,
        double lapseRate,
        List<ScoreBucket> scoreDistribution,
        MaturitySummary maturity,
        OffsetDateTime generatedAt
) {

    /**
     * 单个评分及其出现次数。
     */
    public record ScoreBucket(int score, long count) {
    }

    /**
     * 卡片成熟度分布。复习间隔达到 21 天的卡片视为成熟卡片。
     */
    public record MaturitySummary(
            long newCount,
            long learningCount,
            long youngCount,
            long matureCount,
            long suspendedCount
    ) {
    }
}
