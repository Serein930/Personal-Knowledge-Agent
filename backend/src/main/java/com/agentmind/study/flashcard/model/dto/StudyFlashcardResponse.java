package com.agentmind.study.flashcard.model.dto;

import com.agentmind.study.flashcard.model.StudyFlashcardStatus;
import java.time.OffsetDateTime;

/**
 * 复习卡片对外响应。
 */
public record StudyFlashcardResponse(
        Long id,
        Long workspaceId,
        Long sourceConversationId,
        Long sourceDocumentId,
        String topic,
        String requestId,
        String question,
        String answer,
        String explanation,
        StudyFlashcardStatus status,
        int repetitionCount,
        int intervalDays,
        double easeFactor,
        int lapseCount,
        OffsetDateTime dueAt,
        OffsetDateTime lastReviewedAt,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
