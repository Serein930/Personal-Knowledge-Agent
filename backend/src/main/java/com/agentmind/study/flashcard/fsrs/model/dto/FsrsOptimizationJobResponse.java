package com.agentmind.study.flashcard.fsrs.model.dto;

import com.agentmind.study.flashcard.fsrs.model.FsrsOptimizationJobStatus;
import java.time.OffsetDateTime;
import java.util.List;

/** FSRS 参数优化任务响应。 */
public record FsrsOptimizationJobResponse(
        Long id,
        FsrsOptimizationJobStatus status,
        int reviewCount,
        int effectiveObservationCount,
        double observedLapseRate,
        List<Double> previousParameters,
        List<Double> recommendedParameters,
        double previousDesiredRetention,
        double recommendedDesiredRetention,
        double trainingLossBefore,
        double trainingLossAfter,
        double validationLossBefore,
        double validationLossAfter,
        boolean accepted,
        boolean applied,
        Long appliedVersion,
        String message,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {
}
