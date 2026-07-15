package com.agentmind.study.session.model;

import java.time.OffsetDateTime;

/**
 * 一次有边界的复习会话。
 *
 * <p>会话在创建时固定到期卡片数量，后续卡片新增或排期变化不会改变本次队列，避免用户复习过程中
 * 总任务数跳动。会话只记录进度，具体评分事实仍由不可变复习记录保存。</p>
 */
public record StudyReviewSession(
        Long id,
        Long ownerUserId,
        Long workspaceId,
        StudyReviewSessionStatus status,
        int totalCards,
        int reviewedCards,
        int correctCards,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public StudyReviewSession withId(Long generatedId) {
        return new StudyReviewSession(
                generatedId, ownerUserId, workspaceId, status, totalCards, reviewedCards, correctCards,
                startedAt, completedAt, createdAt, updatedAt
        );
    }
}
