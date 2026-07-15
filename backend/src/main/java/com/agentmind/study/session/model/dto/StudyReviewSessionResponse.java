package com.agentmind.study.session.model.dto;

import com.agentmind.study.flashcard.model.dto.StudyFlashcardResponse;
import com.agentmind.study.session.model.StudyReviewSessionItemStatus;
import com.agentmind.study.session.model.StudyReviewSessionStatus;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 复习会话和固定卡片队列响应。
 */
public record StudyReviewSessionResponse(
        Long id,
        Long workspaceId,
        StudyReviewSessionStatus status,
        int totalCards,
        int reviewedCards,
        int correctCards,
        double progress,
        List<QueueItem> queue,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {

    /**
     * 队列项携带卡片展示数据，前端不需要再逐张发起查询。
     */
    public record QueueItem(
            Long id,
            int position,
            StudyReviewSessionItemStatus status,
            Integer score,
            OffsetDateTime reviewedAt,
            StudyFlashcardResponse flashcard
    ) {
    }
}
