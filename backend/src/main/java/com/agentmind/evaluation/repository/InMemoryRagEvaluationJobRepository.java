package com.agentmind.evaluation.repository;

import com.agentmind.evaluation.model.RagEvaluationJob;
import com.agentmind.evaluation.model.RagEvaluationJobStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** 评估任务内存适配器。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.evaluation", name = "store", havingValue = "memory", matchIfMissing = true)
public class InMemoryRagEvaluationJobRepository implements RagEvaluationJobRepository {

    private final AtomicLong ids = new AtomicLong();
    private final ConcurrentHashMap<Long, RagEvaluationJob> jobs = new ConcurrentHashMap<>();

    @Override
    public synchronized RagEvaluationJob save(RagEvaluationJob job) {
        long id = job.id() == null ? ids.incrementAndGet() : job.id();
        RagEvaluationJob saved = copy(id, job);
        jobs.put(id, saved);
        return saved;
    }

    @Override
    public synchronized Optional<RagEvaluationJob> updateIfStatus(
            RagEvaluationJob job,
            Set<RagEvaluationJobStatus> expectedStatuses
    ) {
        RagEvaluationJob current = jobs.get(job.id());
        if (current == null || !expectedStatuses.contains(current.status())) {
            return Optional.empty();
        }
        RagEvaluationJob saved = copy(job.id(), job);
        jobs.put(saved.id(), saved);
        return Optional.of(saved);
    }

    @Override
    public Optional<RagEvaluationJob> findByScopeAndId(Long ownerUserId, Long workspaceId, Long jobId) {
        return Optional.ofNullable(jobs.get(jobId)).filter(value ->
                value.ownerUserId().equals(ownerUserId) && value.workspaceId().equals(workspaceId));
    }

    @Override
    public Optional<RagEvaluationJob> findLatestSuccessful(
            Long ownerUserId,
            Long workspaceId,
            Long datasetId,
            int datasetVersion
    ) {
        return jobs.values().stream().filter(value ->
                        value.ownerUserId().equals(ownerUserId)
                                && value.workspaceId().equals(workspaceId)
                                && value.datasetId().equals(datasetId)
                                && value.datasetVersion() == datasetVersion
                                && value.status() == RagEvaluationJobStatus.SUCCEEDED)
                .max(Comparator.comparing(RagEvaluationJob::completedAt).thenComparing(RagEvaluationJob::id));
    }

    @Override
    public Optional<RagEvaluationJob> findLatestSuccessfulByScope(Long ownerUserId, Long workspaceId) {
        return jobs.values().stream().filter(value ->
                        value.ownerUserId().equals(ownerUserId)
                                && value.workspaceId().equals(workspaceId)
                                && value.status() == RagEvaluationJobStatus.SUCCEEDED)
                .max(Comparator.comparing(RagEvaluationJob::completedAt).thenComparing(RagEvaluationJob::id));
    }

    @Override
    public List<RagEvaluationJob> findByScope(Long ownerUserId, Long workspaceId, int offset, int limit) {
        return jobs.values().stream()
                .filter(value -> value.ownerUserId().equals(ownerUserId) && value.workspaceId().equals(workspaceId))
                .sorted(Comparator.comparing(RagEvaluationJob::createdAt).reversed()
                        .thenComparing(RagEvaluationJob::id, Comparator.reverseOrder()))
                .skip(offset).limit(limit).toList();
    }

    @Override
    public List<RagEvaluationJob> findSuccessfulByDataset(
            Long ownerUserId,
            Long workspaceId,
            Long datasetId,
            Integer datasetVersion,
            int limit
    ) {
        return jobs.values().stream().filter(value ->
                        value.ownerUserId().equals(ownerUserId)
                                && value.workspaceId().equals(workspaceId)
                                && value.datasetId().equals(datasetId)
                                && (datasetVersion == null || value.datasetVersion() == datasetVersion)
                                && value.status() == RagEvaluationJobStatus.SUCCEEDED)
                .sorted(Comparator.comparing(RagEvaluationJob::completedAt).reversed()
                        .thenComparing(RagEvaluationJob::id, Comparator.reverseOrder()))
                .limit(limit).toList();
    }

    @Override
    public long countByScope(Long ownerUserId, Long workspaceId, RagEvaluationJobStatus status) {
        return jobs.values().stream().filter(value ->
                value.ownerUserId().equals(ownerUserId)
                        && value.workspaceId().equals(workspaceId)
                        && (status == null || value.status() == status)).count();
    }

    private RagEvaluationJob copy(Long id, RagEvaluationJob job) {
        return new RagEvaluationJob(
                id, job.ownerUserId(), job.workspaceId(), job.datasetId(), job.datasetVersion(), job.status(),
                job.retrievalStrategy(), job.topK(), job.promptVersion(), job.modelName(), job.experimentConfig(),
                job.baselineJobId(), job.retryOfJobId(), job.totalCases(), job.completedCases(), job.progress(),
                job.metrics(), job.qualityGate(), job.qualityGateResult(),
                job.caseResults() == null ? List.of() : List.copyOf(job.caseResults()),
                job.failureReason(), job.createdAt(), job.startedAt(), job.updatedAt(), job.completedAt()
        );
    }
}
