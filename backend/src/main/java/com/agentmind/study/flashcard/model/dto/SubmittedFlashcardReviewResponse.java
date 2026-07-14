package com.agentmind.study.flashcard.model.dto;

/**
 * 评分提交结果。
 *
 * @param reused true 表示该请求编号此前已经成功处理，本次没有再次推进调度状态
 */
public record SubmittedFlashcardReviewResponse(
        StudyFlashcardResponse flashcard,
        StudyFlashcardReviewResponse review,
        boolean reused
) {
}
