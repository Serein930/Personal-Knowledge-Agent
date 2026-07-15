package com.agentmind.study.flashcard.fsrs.model;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 一次用户级 FSRS 权重拟合任务。
 *
 * <p>任务同时保留拟合前后权重、训练损失和验证损失。即使结果未通过验证或用户选择不应用，
 * 仍能解释优化器做过什么，而不是只留下一个模糊的成功状态。</p>
 */
public record FsrsOptimizationJob(
        Long id,
        Long ownerUserId,
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

    public FsrsOptimizationJob {
        previousParameters = List.copyOf(previousParameters);
        recommendedParameters = List.copyOf(recommendedParameters);
    }

    public FsrsOptimizationJob withId(Long generatedId) {
        return new FsrsOptimizationJob(
                generatedId, ownerUserId, status, reviewCount, effectiveObservationCount,
                observedLapseRate, previousParameters, recommendedParameters,
                previousDesiredRetention, recommendedDesiredRetention,
                trainingLossBefore, trainingLossAfter, validationLossBefore, validationLossAfter,
                accepted, applied, appliedVersion, message, createdAt, completedAt
        );
    }
}
