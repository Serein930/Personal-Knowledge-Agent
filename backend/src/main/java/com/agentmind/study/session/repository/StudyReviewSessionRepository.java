package com.agentmind.study.session.repository;

import com.agentmind.study.session.model.StudyReviewSession;
import com.agentmind.study.session.model.StudyReviewSessionItem;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import com.agentmind.study.session.model.StudyReviewSessionStatus;

/**
 * 复习会话聚合存储端口。
 */
public interface StudyReviewSessionRepository {

    StudyReviewSession create(StudyReviewSession session, List<StudyReviewSessionItem> items);

    Optional<StudyReviewSession> findByScopeAndId(Long ownerUserId, Long workspaceId, Long sessionId);

    List<StudyReviewSession> findByScope(Long ownerUserId, Long workspaceId, int offset, int limit);

    long countByScope(Long ownerUserId, Long workspaceId);

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

    /** 按预期状态原子推进会话生命周期，避免暂停、恢复和评分请求互相覆盖。 */
    Optional<StudyReviewSession> transitionStatus(
            Long ownerUserId,
            Long workspaceId,
            Long sessionId,
            StudyReviewSessionStatus expectedStatus,
            StudyReviewSessionStatus nextStatus,
            OffsetDateTime changedAt
    );
}
