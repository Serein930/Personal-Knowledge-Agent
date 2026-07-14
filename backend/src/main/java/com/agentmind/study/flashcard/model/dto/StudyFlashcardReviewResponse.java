package com.agentmind.study.flashcard.model.dto;

import com.agentmind.study.flashcard.model.StudyFlashcardStatus;
import java.time.OffsetDateTime;

/**
 * 单次复习记录响应。
 */
public record StudyFlashcardReviewResponse(
        Long id,
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
}
