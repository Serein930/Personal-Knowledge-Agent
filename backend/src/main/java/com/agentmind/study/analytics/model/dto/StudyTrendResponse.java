package com.agentmind.study.analytics.model.dto;

import java.time.LocalDate;
import java.util.List;

/** 指定日期范围内的每日与每周学习趋势。 */
public record StudyTrendResponse(
        LocalDate from,
        LocalDate to,
        long totalReviews,
        long uniqueFlashcards,
        int activeDays,
        double accuracy,
        List<DailyPoint> daily,
        List<WeeklyPoint> weekly
) {

    /** 单日学习聚合点。 */
    public record DailyPoint(
            LocalDate date,
            long reviewCount,
            long uniqueFlashcards,
            long correctCount,
            long lapseCount,
            double accuracy
    ) {
    }

    /** 以周一为起点的自然周聚合点。 */
    public record WeeklyPoint(
            LocalDate weekStart,
            LocalDate weekEnd,
            long reviewCount,
            long uniqueFlashcards,
            int activeDays,
            long correctCount,
            long lapseCount,
            double accuracy
    ) {
    }
}
