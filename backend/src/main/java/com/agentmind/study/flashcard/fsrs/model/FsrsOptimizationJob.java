package com.agentmind.study.flashcard.fsrs.model;

import java.time.OffsetDateTime;

/**
 * 一次用户级 FSRS 参数优化任务。
 *
 * <p>当前实现先校准期望保持率并完整记录样本量、遗忘率和应用结果。权重优化由可替换端口承载，
 * 后续接入专用优化器时不需要改变任务和接口契约。</p>
 */
public record FsrsOptimizationJob(
        Long id,
        Long ownerUserId,
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

    public FsrsOptimizationJob withId(Long generatedId) {
        return new FsrsOptimizationJob(
                generatedId, ownerUserId, status, reviewCount, observedLapseRate,
                previousDesiredRetention, recommendedDesiredRetention, applied,
                message, createdAt, completedAt
        );
    }
}
