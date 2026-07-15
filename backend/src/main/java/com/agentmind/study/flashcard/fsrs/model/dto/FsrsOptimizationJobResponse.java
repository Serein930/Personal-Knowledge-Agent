package com.agentmind.study.flashcard.fsrs.model.dto;

import com.agentmind.study.flashcard.fsrs.model.FsrsOptimizationJobStatus;
import java.time.OffsetDateTime;

/** FSRS 参数优化任务响应。 */
public record FsrsOptimizationJobResponse(
        Long id,
        FsrsOptimizationJobStatus status,
        int reviewCount,
        double observedLapseRate,
        double previousDesiredRetention,
        double recommendedDesiredRetention,
        boolean applied,
        String message,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {
}
