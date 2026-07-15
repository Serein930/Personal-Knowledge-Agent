package com.agentmind.study.flashcard.repository;

import com.agentmind.study.flashcard.model.StudyFlashcardReview;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * 复习评分记录内存适配器。
 */
@Repository
@ConditionalOnProperty(
        prefix = "agentmind.agent.persistence",
        name = "store",
        havingValue = "memory",
        matchIfMissing = true
)
public class InMemoryStudyFlashcardReviewRepository implements StudyFlashcardReviewRepository {

    private final AtomicLong idGenerator = new AtomicLong(1);
    private final ConcurrentHashMap<Long, StudyFlashcardReview> reviews = new ConcurrentHashMap<>();

    @Override
    public synchronized StudyFlashcardReview save(StudyFlashcardReview review) {
        StudyFlashcardReview existing = findByOwnerUserIdAndWorkspaceIdAndRequestId(
                review.ownerUserId(), review.workspaceId(), review.requestId()
        ).orElse(null);
        if (existing != null) {
            return existing;
        }
        StudyFlashcardReview stored = review.id() == null
                ? review.withId(idGenerator.getAndIncrement())
                : review;
        reviews.put(stored.id(), stored);
        return stored;
    }

    @Override
    public Optional<StudyFlashcardReview> findByOwnerUserIdAndWorkspaceIdAndRequestId(
            Long ownerUserId,
            Long workspaceId,
            String requestId
    ) {
        return reviews.values().stream()
                .filter(item -> ownerUserId.equals(item.ownerUserId()))
                .filter(item -> workspaceId.equals(item.workspaceId()))
                .filter(item -> requestId.equals(item.requestId()))
                .findFirst();
    }

    @Override
    public List<StudyFlashcardReview> findByOwnerUserIdAndWorkspaceIdAndFlashcardId(
            Long ownerUserId,
            Long workspaceId,
            Long flashcardId,
            int offset,
            int limit
    ) {
        return reviews.values().stream()
                .filter(item -> ownerUserId.equals(item.ownerUserId()))
                .filter(item -> workspaceId.equals(item.workspaceId()))
                .filter(item -> flashcardId.equals(item.flashcardId()))
                .sorted(Comparator.comparing(StudyFlashcardReview::reviewedAt)
                        .reversed()
                        .thenComparing(StudyFlashcardReview::id, Comparator.reverseOrder()))
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public long countByOwnerUserIdAndWorkspaceIdAndFlashcardId(
            Long ownerUserId,
            Long workspaceId,
            Long flashcardId
    ) {
        return reviews.values().stream()
                .filter(item -> ownerUserId.equals(item.ownerUserId()))
                .filter(item -> workspaceId.equals(item.workspaceId()))
                .filter(item -> flashcardId.equals(item.flashcardId()))
                .count();
    }

    @Override
    public List<StudyFlashcardReview> findChronologicalByOwnerUserIdAndWorkspaceIdAndFlashcardId(
            Long ownerUserId,
            Long workspaceId,
            Long flashcardId
    ) {
        return reviews.values().stream()
                .filter(item -> ownerUserId.equals(item.ownerUserId()))
                .filter(item -> workspaceId.equals(item.workspaceId()))
                .filter(item -> flashcardId.equals(item.flashcardId()))
                .sorted(Comparator.comparing(StudyFlashcardReview::reviewedAt)
                        .thenComparing(StudyFlashcardReview::id))
                .toList();
    }

    @Override
    public List<StudyFlashcardReview> findAllByOwnerUserIdAndWorkspaceId(Long ownerUserId, Long workspaceId) {
        return reviews.values().stream()
                .filter(item -> ownerUserId.equals(item.ownerUserId()))
                .filter(item -> workspaceId.equals(item.workspaceId()))
                .sorted(Comparator.comparing(StudyFlashcardReview::reviewedAt)
                        .thenComparing(StudyFlashcardReview::id))
                .toList();
    }

    @Override
    public List<StudyFlashcardReview> findAllByOwnerUserId(Long ownerUserId) {
        return reviews.values().stream()
                .filter(item -> ownerUserId.equals(item.ownerUserId()))
                .sorted(Comparator.comparing(StudyFlashcardReview::reviewedAt)
                        .thenComparing(StudyFlashcardReview::id))
                .toList();
    }
}
