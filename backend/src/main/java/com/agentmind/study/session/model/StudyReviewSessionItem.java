package com.agentmind.study.session.model;

import java.time.OffsetDateTime;

/**
 * 复习会话中的固定队列项。
 */
public record StudyReviewSessionItem(
        Long id,
        Long ownerUserId,
        Long workspaceId,
        Long sessionId,
        Long flashcardId,
        int position,
        StudyReviewSessionItemStatus status,
        Integer score,
        OffsetDateTime reviewedAt,
        OffsetDateTime createdAt
) {

    public StudyReviewSessionItem withIdentity(Long generatedId, Long generatedSessionId) {
        return new StudyReviewSessionItem(
                generatedId, ownerUserId, workspaceId, generatedSessionId, flashcardId, position,
                status, score, reviewedAt, createdAt
        );
    }
}
