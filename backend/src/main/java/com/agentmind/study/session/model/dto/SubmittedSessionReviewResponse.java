package com.agentmind.study.session.model.dto;

import com.agentmind.study.flashcard.model.dto.SubmittedFlashcardReviewResponse;

/**
 * 会话内评分结果，同时返回最新会话进度和卡片调度结果。
 */
public record SubmittedSessionReviewResponse(
        StudyReviewSessionResponse session,
        SubmittedFlashcardReviewResponse review
) {
}
