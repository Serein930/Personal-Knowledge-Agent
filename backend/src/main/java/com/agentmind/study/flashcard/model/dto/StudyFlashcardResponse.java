package com.agentmind.study.flashcard.model.dto;

import java.time.OffsetDateTime;

/**
 * 复习卡片对外响应。
 */
public record StudyFlashcardResponse(
        Long id,
        Long workspaceId,
        Long sourceConversationId,
        String requestId,
        String question,
        String answer,
        String explanation,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
