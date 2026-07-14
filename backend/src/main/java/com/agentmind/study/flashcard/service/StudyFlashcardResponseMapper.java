package com.agentmind.study.flashcard.service;

import com.agentmind.study.flashcard.model.StudyFlashcard;
import com.agentmind.study.flashcard.model.StudyFlashcardReview;
import com.agentmind.study.flashcard.model.dto.StudyFlashcardResponse;
import com.agentmind.study.flashcard.model.dto.StudyFlashcardReviewResponse;
import org.springframework.stereotype.Component;

/**
 * 复习卡片领域模型到安全响应 DTO 的集中映射器。
 */
@Component
public class StudyFlashcardResponseMapper {

    public StudyFlashcardResponse toFlashcardResponse(StudyFlashcard flashcard) {
        return new StudyFlashcardResponse(
                flashcard.id(),
                flashcard.workspaceId(),
                flashcard.sourceConversationId(),
                flashcard.requestId(),
                flashcard.question(),
                flashcard.answer(),
                flashcard.explanation(),
                flashcard.status(),
                flashcard.repetitionCount(),
                flashcard.intervalDays(),
                flashcard.easeFactor(),
                flashcard.lapseCount(),
                flashcard.dueAt(),
                flashcard.lastReviewedAt(),
                flashcard.version(),
                flashcard.createdAt(),
                flashcard.updatedAt()
        );
    }

    public StudyFlashcardReviewResponse toReviewResponse(StudyFlashcardReview review) {
        return new StudyFlashcardReviewResponse(
                review.id(),
                review.workspaceId(),
                review.flashcardId(),
                review.requestId(),
                review.score(),
                review.previousStatus(),
                review.nextStatus(),
                review.previousIntervalDays(),
                review.nextIntervalDays(),
                review.previousEaseFactor(),
                review.nextEaseFactor(),
                review.previousDueAt(),
                review.nextDueAt(),
                review.algorithm(),
                review.reviewedAt(),
                review.createdAt()
        );
    }
}
