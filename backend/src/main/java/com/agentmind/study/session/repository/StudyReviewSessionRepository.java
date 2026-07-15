package com.agentmind.study.session.repository;

import com.agentmind.study.session.model.StudyReviewSession;
import com.agentmind.study.session.model.StudyReviewSessionItem;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 复习会话聚合存储端口。
 */
public interface StudyReviewSessionRepository {

    StudyReviewSession create(StudyReviewSession session, List<StudyReviewSessionItem> items);

    Optional<StudyReviewSession> findByScopeAndId(Long ownerUserId, Long workspaceId, Long sessionId);

    List<StudyReviewSessionItem> findItemsByScopeAndSessionId(
            Long ownerUserId,
            Long workspaceId,
            Long sessionId
    );

    /**
     * 将仍为待复习的队列项原子标记为已完成，并同步推进会话计数；重复调用不得重复计数。
     */
    StudyReviewSession markReviewed(
            Long ownerUserId,
            Long workspaceId,
            Long sessionId,
            Long flashcardId,
            int score,
            OffsetDateTime reviewedAt
    );
}
