package com.agentmind.study.flashcard.repository;

import com.agentmind.study.flashcard.model.StudyFlashcard;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

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

    /**
     * 统计和计划生成需要读取知识空间内全部卡片。仓储实现应始终带用户与知识空间过滤。
     */
    List<StudyFlashcard> findAllByOwnerUserIdAndWorkspaceId(Long ownerUserId, Long workspaceId);

    Optional<StudyFlashcard> findByOwnerUserIdAndWorkspaceIdAndId(
            Long ownerUserId,
            Long workspaceId,
            Long flashcardId
    );

    List<StudyFlashcard> findDueByOwnerUserIdAndWorkspaceId(
            Long ownerUserId,
            Long workspaceId,
            OffsetDateTime dueBefore,
            int offset,
            int limit
    );

    long countDueByOwnerUserIdAndWorkspaceId(
            Long ownerUserId,
            Long workspaceId,
            OffsetDateTime dueBefore
    );

    /**
     * 使用旧版本号原子更新调度状态。版本不匹配时返回空，调用方应重新读取并计算。
     */
    Optional<StudyFlashcard> updateSchedule(StudyFlashcard flashcard, long expectedVersion);
}
