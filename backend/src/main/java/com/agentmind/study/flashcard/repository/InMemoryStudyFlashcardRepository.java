package com.agentmind.study.flashcard.repository;

import com.agentmind.study.flashcard.model.StudyFlashcard;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.time.OffsetDateTime;
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
                ? flashcard.withId(idGenerator.getAndIncrement())
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

    @Override
    public List<StudyFlashcard> findAllByOwnerUserIdAndWorkspaceId(Long ownerUserId, Long workspaceId) {
        return flashcards.values().stream()
                .filter(flashcard -> ownerUserId.equals(flashcard.ownerUserId()))
                .filter(flashcard -> workspaceId.equals(flashcard.workspaceId()))
                .sorted(Comparator.comparing(StudyFlashcard::createdAt).thenComparing(StudyFlashcard::id))
                .toList();
    }

    @Override
    public Optional<StudyFlashcard> findByOwnerUserIdAndWorkspaceIdAndId(
            Long ownerUserId,
            Long workspaceId,
            Long flashcardId
    ) {
        return Optional.ofNullable(flashcards.get(flashcardId))
                .filter(item -> ownerUserId.equals(item.ownerUserId()))
                .filter(item -> workspaceId.equals(item.workspaceId()));
    }

    @Override
    public List<StudyFlashcard> findDueByOwnerUserIdAndWorkspaceId(
            Long ownerUserId,
            Long workspaceId,
            OffsetDateTime dueBefore,
            int offset,
            int limit
    ) {
        return flashcards.values().stream()
                .filter(item -> ownerUserId.equals(item.ownerUserId()))
                .filter(item -> workspaceId.equals(item.workspaceId()))
                .filter(item -> item.status() != com.agentmind.study.flashcard.model.StudyFlashcardStatus.SUSPENDED)
                .filter(item -> !item.dueAt().isAfter(dueBefore))
                .sorted(Comparator.comparing(StudyFlashcard::dueAt).thenComparing(StudyFlashcard::id))
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public long countDueByOwnerUserIdAndWorkspaceId(
            Long ownerUserId,
            Long workspaceId,
            OffsetDateTime dueBefore
    ) {
        return flashcards.values().stream()
                .filter(item -> ownerUserId.equals(item.ownerUserId()))
                .filter(item -> workspaceId.equals(item.workspaceId()))
                .filter(item -> item.status() != com.agentmind.study.flashcard.model.StudyFlashcardStatus.SUSPENDED)
                .filter(item -> !item.dueAt().isAfter(dueBefore))
                .count();
    }

    @Override
    public Optional<StudyFlashcard> updateSchedule(StudyFlashcard flashcard, long expectedVersion) {
        AtomicReference<StudyFlashcard> updated = new AtomicReference<>();
        flashcards.computeIfPresent(flashcard.id(), (ignored, current) -> {
            if (!current.ownerUserId().equals(flashcard.ownerUserId())
                    || !current.workspaceId().equals(flashcard.workspaceId())
                    || current.version() != expectedVersion) {
                return current;
            }
            updated.set(flashcard);
            return flashcard;
        });
        return Optional.ofNullable(updated.get());
    }
}
