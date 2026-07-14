package com.agentmind.study.flashcard.repository;

import com.agentmind.study.flashcard.model.StudyFlashcard;
import java.util.List;

/**
 * 复习卡片存储端口。
 */
public interface StudyFlashcardRepository {

    StudyFlashcard save(StudyFlashcard flashcard);

    List<StudyFlashcard> findByOwnerUserIdAndWorkspaceId(
            Long ownerUserId,
            Long workspaceId,
            int offset,
            int limit
    );

    long countByOwnerUserIdAndWorkspaceId(Long ownerUserId, Long workspaceId);
}
