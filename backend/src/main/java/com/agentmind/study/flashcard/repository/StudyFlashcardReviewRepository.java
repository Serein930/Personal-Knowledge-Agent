package com.agentmind.study.flashcard.repository;

import com.agentmind.study.flashcard.model.StudyFlashcardReview;
import java.util.List;
import java.util.Optional;
import com.agentmind.study.maintenance.model.StudyDataScope;

/**
 * 复习评分记录存储端口。
 */
public interface StudyFlashcardReviewRepository {

    StudyFlashcardReview save(StudyFlashcardReview review);

    Optional<StudyFlashcardReview> findByOwnerUserIdAndWorkspaceIdAndRequestId(
            Long ownerUserId,
            Long workspaceId,
            String requestId
    );

    List<StudyFlashcardReview> findByOwnerUserIdAndWorkspaceIdAndFlashcardId(
            Long ownerUserId,
            Long workspaceId,
            Long flashcardId,
            int offset,
            int limit
    );

    long countByOwnerUserIdAndWorkspaceIdAndFlashcardId(
            Long ownerUserId,
            Long workspaceId,
            Long flashcardId
    );

    /**
     * 按时间正序返回单卡完整历史，供 FSRS 重放算法状态。
     */
    List<StudyFlashcardReview> findChronologicalByOwnerUserIdAndWorkspaceIdAndFlashcardId(
            Long ownerUserId,
            Long workspaceId,
            Long flashcardId
    );

    /**
     * 返回知识空间全部复习记录，供当前阶段完成统计聚合。数据规模增长后可由数据库聚合投影替换。
     */
    List<StudyFlashcardReview> findAllByOwnerUserIdAndWorkspaceId(Long ownerUserId, Long workspaceId);

    /** 返回用户跨知识空间的全部复习事实，供用户级 FSRS 参数优化使用。 */
    List<StudyFlashcardReview> findAllByOwnerUserId(Long ownerUserId);

    /** 返回最近有复习行为的用户与知识空间，供后台维护分批处理。 */
    List<StudyDataScope> findActiveScopes(int limit);
}
