package com.agentmind.study.session.repository;

import com.agentmind.study.session.model.StudyReviewSession;
import com.agentmind.study.session.model.StudyReviewSessionItem;
import com.agentmind.study.session.model.StudyReviewSessionItemStatus;
import com.agentmind.study.session.model.StudyReviewSessionStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * 复习会话内存适配器。
 */
@Repository
@ConditionalOnProperty(
        prefix = "agentmind.agent.persistence",
        name = "store",
        havingValue = "memory",
        matchIfMissing = true
)
public class InMemoryStudyReviewSessionRepository implements StudyReviewSessionRepository {

    private final AtomicLong sessionIdGenerator = new AtomicLong(1);
    private final AtomicLong itemIdGenerator = new AtomicLong(1);
    private final ConcurrentHashMap<Long, StudyReviewSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, StudyReviewSessionItem> items = new ConcurrentHashMap<>();

    @Override
    public synchronized StudyReviewSession create(
            StudyReviewSession session,
            List<StudyReviewSessionItem> newItems
    ) {
        StudyReviewSession stored = session.withId(sessionIdGenerator.getAndIncrement());
        sessions.put(stored.id(), stored);
        newItems.forEach(item -> {
            StudyReviewSessionItem storedItem = item.withIdentity(
                    itemIdGenerator.getAndIncrement(), stored.id()
            );
            items.put(storedItem.id(), storedItem);
        });
        return stored;
    }

    @Override
    public Optional<StudyReviewSession> findByScopeAndId(
            Long ownerUserId,
            Long workspaceId,
            Long sessionId
    ) {
        return Optional.ofNullable(sessions.get(sessionId))
                .filter(session -> ownerUserId.equals(session.ownerUserId()))
                .filter(session -> workspaceId.equals(session.workspaceId()));
    }

    @Override
    public List<StudyReviewSessionItem> findItemsByScopeAndSessionId(
            Long ownerUserId,
            Long workspaceId,
            Long sessionId
    ) {
        return items.values().stream()
                .filter(item -> ownerUserId.equals(item.ownerUserId()))
                .filter(item -> workspaceId.equals(item.workspaceId()))
                .filter(item -> sessionId.equals(item.sessionId()))
                .sorted(java.util.Comparator.comparing(StudyReviewSessionItem::position))
                .toList();
    }

    @Override
    public synchronized StudyReviewSession markReviewed(
            Long ownerUserId,
            Long workspaceId,
            Long sessionId,
            Long flashcardId,
            int score,
            OffsetDateTime reviewedAt
    ) {
        StudyReviewSession session = findByScopeAndId(ownerUserId, workspaceId, sessionId)
                .orElseThrow(() -> new IllegalStateException("复习会话不存在"));
        StudyReviewSessionItem item = findItemsByScopeAndSessionId(ownerUserId, workspaceId, sessionId).stream()
                .filter(candidate -> flashcardId.equals(candidate.flashcardId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("卡片不在当前复习会话中"));
        if (item.status() == StudyReviewSessionItemStatus.REVIEWED) {
            return session;
        }

        items.put(item.id(), new StudyReviewSessionItem(
                item.id(), item.ownerUserId(), item.workspaceId(), item.sessionId(), item.flashcardId(),
                item.position(), StudyReviewSessionItemStatus.REVIEWED, score, reviewedAt, item.createdAt()
        ));
        int reviewedCards = session.reviewedCards() + 1;
        int correctCards = session.correctCards() + (score >= 3 ? 1 : 0);
        boolean completed = reviewedCards >= session.totalCards();
        StudyReviewSession updated = new StudyReviewSession(
                session.id(), session.ownerUserId(), session.workspaceId(),
                completed ? StudyReviewSessionStatus.COMPLETED : StudyReviewSessionStatus.IN_PROGRESS,
                session.totalCards(), reviewedCards, correctCards, session.startedAt(),
                completed ? reviewedAt : null, session.createdAt(), reviewedAt
        );
        sessions.put(updated.id(), updated);
        return updated;
    }
}
