package com.agentmind.study.flashcard.fsrs.repository;

import com.agentmind.study.flashcard.fsrs.model.FsrsCardSnapshot;
import java.util.Optional;

/** FSRS 卡片快照存储端口。 */
public interface FsrsCardSnapshotRepository {

    Optional<FsrsCardSnapshot> findByScopeAndFlashcardId(
            Long ownerUserId,
            Long workspaceId,
            Long flashcardId
    );

    FsrsCardSnapshot save(FsrsCardSnapshot snapshot);
}
