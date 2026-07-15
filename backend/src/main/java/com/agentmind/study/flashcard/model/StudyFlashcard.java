package com.agentmind.study.flashcard.model;

import java.time.OffsetDateTime;

/**
 * 复习卡片模型。
 *
 * <p>问题与答案构成最小复习单元，解释字段用于保存推理线索或易错点。每张卡片都保留用户、
 * 知识空间和写工具请求编号，支持权限隔离与数据库幂等。</p>
 */
public record StudyFlashcard(
        Long id,
        Long ownerUserId,
        Long workspaceId,
        Long sourceConversationId,
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

    public StudyFlashcard withId(Long generatedId) {
        return new StudyFlashcard(
                generatedId,
                ownerUserId,
                workspaceId,
                sourceConversationId,
                requestId,
                question,
                answer,
                explanation,
                status,
                repetitionCount,
                intervalDays,
                easeFactor,
                lapseCount,
                dueAt,
                lastReviewedAt,
                version,
                createdAt,
                updatedAt
        );
    }

    /**
     * 读取当前卡片的算法调度状态。
     */
    public StudyFlashcardSchedule schedule() {
        return new StudyFlashcardSchedule(
                status,
                repetitionCount,
                intervalDays,
                easeFactor,
                lapseCount,
                dueAt,
                lastReviewedAt
        );
    }

    /**
     * 应用一次算法计算结果。版本号在仓储成功执行条件更新后增加，调用方不能跳过版本校验直接覆盖。
     */
    public StudyFlashcard applySchedule(StudyFlashcardSchedule nextSchedule, OffsetDateTime now) {
        return new StudyFlashcard(
                id,
                ownerUserId,
                workspaceId,
                sourceConversationId,
                requestId,
                question,
                answer,
                explanation,
                nextSchedule.status(),
                nextSchedule.repetitionCount(),
                nextSchedule.intervalDays(),
                nextSchedule.easeFactor(),
                nextSchedule.lapseCount(),
                nextSchedule.dueAt(),
                nextSchedule.lastReviewedAt(),
                version + 1,
                createdAt,
                now
        );
    }

    /**
     * 应用人工管理状态。暂停、恢复和重新排期同样增加版本号，防止管理操作覆盖并发评分结果。
     */
    public StudyFlashcard manage(StudyFlashcardStatus nextStatus, OffsetDateTime nextDueAt, OffsetDateTime now) {
        return new StudyFlashcard(
                id,
                ownerUserId,
                workspaceId,
                sourceConversationId,
                requestId,
                question,
                answer,
                explanation,
                nextStatus,
                repetitionCount,
                intervalDays,
                easeFactor,
                lapseCount,
                nextDueAt,
                lastReviewedAt,
                version + 1,
                createdAt,
                now
        );
    }
}
