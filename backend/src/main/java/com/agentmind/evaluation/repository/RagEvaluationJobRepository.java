package com.agentmind.evaluation.repository;

import com.agentmind.evaluation.model.RagEvaluationJob;
import com.agentmind.evaluation.model.RagEvaluationJobStatus;
import java.util.List;
import java.util.Optional;

/** 评估任务、聚合指标和逐题证据仓储端口。 */
public interface RagEvaluationJobRepository {

    RagEvaluationJob save(RagEvaluationJob job);

    Optional<RagEvaluationJob> findByScopeAndId(Long ownerUserId, Long workspaceId, Long jobId);

    Optional<RagEvaluationJob> findLatestSuccessful(
            Long ownerUserId,
            Long workspaceId,
            Long datasetId,
            int datasetVersion
    );

    Optional<RagEvaluationJob> findLatestSuccessfulByScope(Long ownerUserId, Long workspaceId);

    List<RagEvaluationJob> findByScope(Long ownerUserId, Long workspaceId, int offset, int limit);

    long countByScope(Long ownerUserId, Long workspaceId, RagEvaluationJobStatus status);
}
