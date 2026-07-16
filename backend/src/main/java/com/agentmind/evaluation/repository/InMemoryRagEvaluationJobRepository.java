package com.agentmind.evaluation.repository;

import com.agentmind.evaluation.model.RagEvaluationJob;
import com.agentmind.evaluation.model.RagEvaluationJobStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.time.OffsetDateTime;
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
        RagEvaluationJob saved = copy(job.id(), preserveActiveLease(current, job));
        jobs.put(saved.id(), saved);
        return Optional.of(saved);
    }

    @Override
    public synchronized Optional<RagEvaluationJob> claim(
            Long ownerUserId,
            Long workspaceId,
            Long jobId,
            String leaseOwner,
            OffsetDateTime now,
            OffsetDateTime leaseExpiresAt
    ) {
        RagEvaluationJob current = jobs.get(jobId);
        if (current == null
                || !current.ownerUserId().equals(ownerUserId)
                || !current.workspaceId().equals(workspaceId)
                || current.status() != RagEvaluationJobStatus.PENDING) {
            return Optional.empty();
        }
        RagEvaluationJob claimed = copy(jobId, current.withLease(leaseOwner, now, leaseExpiresAt));
        jobs.put(jobId, claimed);
        return Optional.of(claimed);
    }

    @Override
    public synchronized boolean renewLease(
            Long jobId,
            String leaseOwner,
            OffsetDateTime now,
            OffsetDateTime leaseExpiresAt
    ) {
        RagEvaluationJob current = jobs.get(jobId);
        if (current == null
                || !leaseOwner.equals(current.leaseOwner())
                || current.leaseExpiresAt() == null
                || !current.leaseExpiresAt().isAfter(now)
                || (current.status() != RagEvaluationJobStatus.RUNNING
                    && current.status() != RagEvaluationJobStatus.CANCEL_REQUESTED)) {
            return false;
        }
        jobs.put(jobId, copy(jobId, current.withRenewedLease(now, leaseExpiresAt)));
        return true;
    }

    @Override
    public synchronized Optional<RagEvaluationJob> updateIfStatusAndLeaseOwner(
            RagEvaluationJob job,
            Set<RagEvaluationJobStatus> expectedStatuses,
            String leaseOwner,
            OffsetDateTime now
    ) {
        RagEvaluationJob current = jobs.get(job.id());
        if (current == null
                || !expectedStatuses.contains(current.status())
                || !leaseOwner.equals(current.leaseOwner())
                || current.leaseExpiresAt() == null
                || !current.leaseExpiresAt().isAfter(now)) {
            return Optional.empty();
        }
        RagEvaluationJob saved = copy(job.id(), preserveActiveLease(current, job));
        jobs.put(saved.id(), saved);
        return Optional.of(saved);
    }

    @Override
    public synchronized List<RagEvaluationJob> recoverExpiredLeases(OffsetDateTime now, int limit) {
        List<RagEvaluationJob> expired = jobs.values().stream()
                .filter(job -> (job.status() == RagEvaluationJobStatus.RUNNING
                        || job.status() == RagEvaluationJobStatus.CANCEL_REQUESTED)
                        && job.leaseExpiresAt() != null
                        && !job.leaseExpiresAt().isAfter(now))
                .sorted(Comparator.comparing(RagEvaluationJob::leaseExpiresAt))
                .limit(limit)
                .toList();
        return expired.stream().map(job -> {
            RagEvaluationJobStatus next = job.status() == RagEvaluationJobStatus.CANCEL_REQUESTED
                    ? RagEvaluationJobStatus.CANCELED : RagEvaluationJobStatus.PENDING;
            RagEvaluationJob recovered = copy(job.id(), job.withRecoveredStatus(next, now));
            jobs.put(job.id(), recovered);
            return recovered;
        }).toList();
    }

    @Override
    public List<RagEvaluationJob> findPendingJobs(int limit) {
        return jobs.values().stream()
                .filter(job -> job.status() == RagEvaluationJobStatus.PENDING)
                .sorted(Comparator.comparing(RagEvaluationJob::createdAt).thenComparing(RagEvaluationJob::id))
                .limit(limit)
                .toList();
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
                job.failureReason(), job.attemptCount(), job.recoveryCount(), job.leaseOwner(),
                job.leaseExpiresAt(), job.heartbeatAt(), job.createdAt(), job.startedAt(),
                job.updatedAt(), job.completedAt()
        );
    }

    private RagEvaluationJob preserveActiveLease(RagEvaluationJob current, RagEvaluationJob update) {
        if (update.terminal() || current.leaseExpiresAt() == null) {
            return update;
        }
        return update.withRenewedLease(current.heartbeatAt(), current.leaseExpiresAt());
    }
}
