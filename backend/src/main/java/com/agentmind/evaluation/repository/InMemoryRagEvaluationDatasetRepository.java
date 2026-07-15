package com.agentmind.evaluation.repository;

import com.agentmind.evaluation.model.RagEvaluationDataset;
import com.agentmind.evaluation.model.RagEvaluationDatasetVersion;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** 固定评估集内存适配器，适合无数据库开发和可重复自动测试。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.evaluation", name = "store", havingValue = "memory", matchIfMissing = true)
public class InMemoryRagEvaluationDatasetRepository implements RagEvaluationDatasetRepository {

    private final AtomicLong datasetIds = new AtomicLong();
    private final AtomicLong versionIds = new AtomicLong();
    private final ConcurrentHashMap<Long, RagEvaluationDataset> datasets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, RagEvaluationDatasetVersion> versions = new ConcurrentHashMap<>();

    @Override
    public RagEvaluationDataset saveDataset(RagEvaluationDataset dataset) {
        long id = dataset.id() == null ? datasetIds.incrementAndGet() : dataset.id();
        RagEvaluationDataset saved = new RagEvaluationDataset(
                id, dataset.ownerUserId(), dataset.workspaceId(), dataset.name(), dataset.description(),
                dataset.latestVersion(), dataset.createdAt(), dataset.updatedAt()
        );
        datasets.put(id, saved);
        return saved;
    }

    @Override
    public synchronized RagEvaluationDatasetVersion saveVersion(RagEvaluationDatasetVersion version) {
        RagEvaluationDataset dataset = datasets.get(version.datasetId());
        if (dataset == null || !dataset.ownerUserId().equals(version.ownerUserId())
                || !dataset.workspaceId().equals(version.workspaceId())) {
            throw new IllegalStateException("评估集不存在或归属不一致");
        }
        boolean duplicated = versions.values().stream().anyMatch(current ->
                current.datasetId().equals(version.datasetId()) && current.version() == version.version());
        if (duplicated) {
            throw new IllegalStateException("评估集版本已存在");
        }
        RagEvaluationDatasetVersion saved = new RagEvaluationDatasetVersion(
                versionIds.incrementAndGet(), version.datasetId(), version.ownerUserId(), version.workspaceId(),
                version.version(), version.changeNote(), List.copyOf(version.cases()), version.createdAt()
        );
        versions.put(saved.id(), saved);
        OffsetDateTime now = saved.createdAt();
        datasets.put(dataset.id(), new RagEvaluationDataset(
                dataset.id(), dataset.ownerUserId(), dataset.workspaceId(), dataset.name(), dataset.description(),
                saved.version(), dataset.createdAt(), now
        ));
        return saved;
    }

    @Override
    public Optional<RagEvaluationDataset> findDatasetByScopeAndId(
            Long ownerUserId,
            Long workspaceId,
            Long datasetId
    ) {
        return Optional.ofNullable(datasets.get(datasetId)).filter(value ->
                value.ownerUserId().equals(ownerUserId) && value.workspaceId().equals(workspaceId));
    }

    @Override
    public Optional<RagEvaluationDatasetVersion> findVersionByScopeAndDatasetIdAndVersion(
            Long ownerUserId,
            Long workspaceId,
            Long datasetId,
            int version
    ) {
        return versions.values().stream().filter(value ->
                value.ownerUserId().equals(ownerUserId)
                        && value.workspaceId().equals(workspaceId)
                        && value.datasetId().equals(datasetId)
                        && value.version() == version).findFirst();
    }

    @Override
    public List<RagEvaluationDataset> findDatasetsByScope(
            Long ownerUserId,
            Long workspaceId,
            int offset,
            int limit
    ) {
        return datasets.values().stream()
                .filter(value -> value.ownerUserId().equals(ownerUserId) && value.workspaceId().equals(workspaceId))
                .sorted(Comparator.comparing(RagEvaluationDataset::updatedAt).reversed()
                        .thenComparing(RagEvaluationDataset::id, Comparator.reverseOrder()))
                .skip(offset).limit(limit).toList();
    }

    @Override
    public List<RagEvaluationDatasetVersion> findVersionsByScopeAndDatasetId(
            Long ownerUserId,
            Long workspaceId,
            Long datasetId
    ) {
        return versions.values().stream().filter(value ->
                        value.ownerUserId().equals(ownerUserId)
                                && value.workspaceId().equals(workspaceId)
                                && value.datasetId().equals(datasetId))
                .sorted(Comparator.comparingInt(RagEvaluationDatasetVersion::version).reversed()).toList();
    }

    @Override
    public boolean existsByScopeAndName(Long ownerUserId, Long workspaceId, String name) {
        return datasets.values().stream().anyMatch(value ->
                value.ownerUserId().equals(ownerUserId)
                        && value.workspaceId().equals(workspaceId)
                        && value.name().equalsIgnoreCase(name));
    }

    @Override
    public long countDatasetsByScope(Long ownerUserId, Long workspaceId) {
        return datasets.values().stream().filter(value ->
                value.ownerUserId().equals(ownerUserId) && value.workspaceId().equals(workspaceId)).count();
    }
}
