package com.agentmind.evaluation.repository;

import com.agentmind.evaluation.model.RagEvaluationDataset;
import com.agentmind.evaluation.model.RagEvaluationDatasetVersion;
import java.util.List;
import java.util.Optional;

/** 固定评估集及不可变版本仓储端口。 */
public interface RagEvaluationDatasetRepository {

    RagEvaluationDataset saveDataset(RagEvaluationDataset dataset);

    RagEvaluationDatasetVersion saveVersion(RagEvaluationDatasetVersion version);

    Optional<RagEvaluationDataset> findDatasetByScopeAndId(Long ownerUserId, Long workspaceId, Long datasetId);

    Optional<RagEvaluationDatasetVersion> findVersionByScopeAndDatasetIdAndVersion(
            Long ownerUserId,
            Long workspaceId,
            Long datasetId,
            int version
    );

    List<RagEvaluationDataset> findDatasetsByScope(Long ownerUserId, Long workspaceId, int offset, int limit);

    List<RagEvaluationDatasetVersion> findVersionsByScopeAndDatasetId(
            Long ownerUserId,
            Long workspaceId,
            Long datasetId
    );

    boolean existsByScopeAndName(Long ownerUserId, Long workspaceId, String name);

    long countDatasetsByScope(Long ownerUserId, Long workspaceId);
}
