package com.agentmind.study.flashcard.repository;

import com.agentmind.study.flashcard.model.StudyFlashcard;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * 复习卡片内存适配器。
 */
@Repository
@ConditionalOnProperty(
        prefix = "agentmind.agent.persistence",
        name = "store",
        havingValue = "memory",
        matchIfMissing = true
)
public class InMemoryStudyFlashcardRepository implements StudyFlashcardRepository {

    private final AtomicLong idGenerator = new AtomicLong(1);
    private final ConcurrentHashMap<Long, StudyFlashcard> flashcards = new ConcurrentHashMap<>();

    @Override
    public synchronized StudyFlashcard save(StudyFlashcard flashcard) {
        StudyFlashcard existing = flashcards.values().stream()
                .filter(candidate -> candidate.ownerUserId().equals(flashcard.ownerUserId()))
                .filter(candidate -> candidate.workspaceId().equals(flashcard.workspaceId()))
                .filter(candidate -> candidate.requestId().equals(flashcard.requestId()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        StudyFlashcard stored = flashcard.id() == null
                ? new StudyFlashcard(
                        idGenerator.getAndIncrement(), flashcard.ownerUserId(), flashcard.workspaceId(),
                        flashcard.sourceConversationId(), flashcard.requestId(), flashcard.question(),
                        flashcard.answer(), flashcard.explanation(), flashcard.createdAt(), flashcard.updatedAt()
                )
                : flashcard;
        flashcards.put(stored.id(), stored);
        return stored;
    }

    @Override
    public List<StudyFlashcard> findByOwnerUserIdAndWorkspaceId(
            Long ownerUserId,
            Long workspaceId,
            int offset,
            int limit
    ) {
        return flashcards.values().stream()
                .filter(flashcard -> ownerUserId.equals(flashcard.ownerUserId()))
                .filter(flashcard -> workspaceId.equals(flashcard.workspaceId()))
                .sorted(Comparator.comparing(StudyFlashcard::createdAt)
                        .reversed()
                        .thenComparing(StudyFlashcard::id, Comparator.reverseOrder()))
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public long countByOwnerUserIdAndWorkspaceId(Long ownerUserId, Long workspaceId) {
        return flashcards.values().stream()
                .filter(flashcard -> ownerUserId.equals(flashcard.ownerUserId()))
                .filter(flashcard -> workspaceId.equals(flashcard.workspaceId()))
                .count();
    }
}
