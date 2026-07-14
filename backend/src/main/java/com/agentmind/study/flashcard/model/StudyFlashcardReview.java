package com.agentmind.study.flashcard.model;

import java.time.OffsetDateTime;

/**
 * 一次不可变的复习评分记录。
 *
 * <p>记录同时保存算法执行前后的关键状态，便于解释“为什么下次复习被安排到某天”，也为后续比较
 * SM-2 与 FSRS 的效果保留原始事件数据。</p>
 */
public record StudyFlashcardReview(
        Long id,
        Long ownerUserId,
        Long workspaceId,
        Long flashcardId,
        String requestId,
        int score,
        StudyFlashcardStatus previousStatus,
        StudyFlashcardStatus nextStatus,
        int previousIntervalDays,
        int nextIntervalDays,
        double previousEaseFactor,
        double nextEaseFactor,
        OffsetDateTime previousDueAt,
        OffsetDateTime nextDueAt,
        String algorithm,
        OffsetDateTime reviewedAt,
        OffsetDateTime createdAt
) {

    public StudyFlashcardReview withId(Long generatedId) {
        return new StudyFlashcardReview(
                generatedId,
                ownerUserId,
                workspaceId,
                flashcardId,
                requestId,
                score,
                previousStatus,
                nextStatus,
                previousIntervalDays,
                nextIntervalDays,
                previousEaseFactor,
                nextEaseFactor,
                previousDueAt,
                nextDueAt,
                algorithm,
                reviewedAt,
                createdAt
        );
    }
}
