package com.agentmind.study.flashcard.fsrs.repository;

import com.agentmind.study.flashcard.fsrs.model.FsrsCardSnapshot;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** FSRS 卡片快照内存适配器。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.agent.persistence", name = "store", havingValue = "memory", matchIfMissing = true)
public class InMemoryFsrsCardSnapshotRepository implements FsrsCardSnapshotRepository {

    private final ConcurrentHashMap<String, FsrsCardSnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public Optional<FsrsCardSnapshot> findByScopeAndFlashcardId(
            Long ownerUserId,
            Long workspaceId,
            Long flashcardId
    ) {
        return Optional.ofNullable(snapshots.get(key(ownerUserId, workspaceId, flashcardId)));
    }

    @Override
    public FsrsCardSnapshot save(FsrsCardSnapshot snapshot) {
        snapshots.put(key(snapshot.ownerUserId(), snapshot.workspaceId(), snapshot.flashcardId()), snapshot);
        return snapshot;
    }

    private String key(Long ownerUserId, Long workspaceId, Long flashcardId) {
        return ownerUserId + ":" + workspaceId + ":" + flashcardId;
    }
}
