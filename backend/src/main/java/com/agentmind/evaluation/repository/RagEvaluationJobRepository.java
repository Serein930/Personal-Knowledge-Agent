package com.agentmind.evaluation.repository;

import com.agentmind.evaluation.model.RagEvaluationJob;
import com.agentmind.evaluation.model.RagEvaluationJobStatus;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 评估任务、聚合指标和逐题证据仓储端口。 */
public interface RagEvaluationJobRepository {

    RagEvaluationJob save(RagEvaluationJob job);

    /**
     * 仅当数据库中的当前状态属于期望集合时更新任务。
     *
     * <p>异步执行、取消请求和多实例并发可能同时修改同一任务，因此状态迁移不能使用普通覆盖更新。
     * 返回空表示其他线程已经抢先完成迁移，调用方应重新读取任务后决定下一步。</p>
     */
    Optional<RagEvaluationJob> updateIfStatus(
            RagEvaluationJob job,
            Set<RagEvaluationJobStatus> expectedStatuses
    );

    Optional<RagEvaluationJob> findByScopeAndId(Long ownerUserId, Long workspaceId, Long jobId);

    Optional<RagEvaluationJob> findLatestSuccessful(
            Long ownerUserId,
            Long workspaceId,
            Long datasetId,
            int datasetVersion
    );

    Optional<RagEvaluationJob> findLatestSuccessfulByScope(Long ownerUserId, Long workspaceId);

    List<RagEvaluationJob> findByScope(Long ownerUserId, Long workspaceId, int offset, int limit);

    /** 查询指定评估集的成功任务，用于绘制同一数据集上的指标趋势。 */
    List<RagEvaluationJob> findSuccessfulByDataset(
            Long ownerUserId,
            Long workspaceId,
            Long datasetId,
            Integer datasetVersion,
            int limit
    );

    long countByScope(Long ownerUserId, Long workspaceId, RagEvaluationJobStatus status);
}
