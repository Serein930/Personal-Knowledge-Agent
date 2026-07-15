package com.agentmind.study.flashcard.fsrs.repository;

import com.agentmind.study.flashcard.fsrs.model.FsrsOptimizationJob;
import java.util.List;

/** FSRS 参数优化任务存储端口。 */
public interface FsrsOptimizationJobRepository {

    FsrsOptimizationJob save(FsrsOptimizationJob job);

    List<FsrsOptimizationJob> findByOwnerUserId(Long ownerUserId, int offset, int limit);

    long countByOwnerUserId(Long ownerUserId);
}
