package com.agentmind.study.flashcard.repository;

import com.agentmind.study.flashcard.model.StudyFlashcardReview;
import java.util.List;
import java.util.Optional;

/**
 * 复习评分记录存储端口。
 */
public interface StudyFlashcardReviewRepository {

    StudyFlashcardReview save(StudyFlashcardReview review);

    Optional<StudyFlashcardReview> findByOwnerUserIdAndWorkspaceIdAndRequestId(
            Long ownerUserId,
            Long workspaceId,
            String requestId
    );

    List<StudyFlashcardReview> findByOwnerUserIdAndWorkspaceIdAndFlashcardId(
            Long ownerUserId,
            Long workspaceId,
            Long flashcardId,
            int offset,
            int limit
    );

    long countByOwnerUserIdAndWorkspaceIdAndFlashcardId(
            Long ownerUserId,
            Long workspaceId,
            Long flashcardId
    );
}
