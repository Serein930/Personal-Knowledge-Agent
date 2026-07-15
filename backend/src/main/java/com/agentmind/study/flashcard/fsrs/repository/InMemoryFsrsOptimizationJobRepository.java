package com.agentmind.study.flashcard.fsrs.repository;

import com.agentmind.study.flashcard.fsrs.model.FsrsOptimizationJob;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** FSRS 参数优化任务内存适配器。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.agent.persistence", name = "store", havingValue = "memory", matchIfMissing = true)
public class InMemoryFsrsOptimizationJobRepository implements FsrsOptimizationJobRepository {

    private final AtomicLong idGenerator = new AtomicLong(1);
    private final ConcurrentHashMap<Long, FsrsOptimizationJob> jobs = new ConcurrentHashMap<>();

    @Override
    public synchronized FsrsOptimizationJob save(FsrsOptimizationJob job) {
        FsrsOptimizationJob stored = job.id() == null ? job.withId(idGenerator.getAndIncrement()) : job;
        jobs.put(stored.id(), stored);
        return stored;
    }

    @Override
    public List<FsrsOptimizationJob> findByOwnerUserId(Long ownerUserId, int offset, int limit) {
        return jobs.values().stream()
                .filter(job -> ownerUserId.equals(job.ownerUserId()))
                .sorted(Comparator.comparing(FsrsOptimizationJob::createdAt).reversed()
                        .thenComparing(FsrsOptimizationJob::id, Comparator.reverseOrder()))
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public long countByOwnerUserId(Long ownerUserId) {
        return jobs.values().stream().filter(job -> ownerUserId.equals(job.ownerUserId())).count();
    }

    @Override
    public Optional<FsrsOptimizationJob> findLatestByOwnerUserId(Long ownerUserId) {
        return jobs.values().stream()
                .filter(job -> ownerUserId.equals(job.ownerUserId()))
                .max(Comparator.comparing(FsrsOptimizationJob::createdAt)
                        .thenComparing(FsrsOptimizationJob::id));
    }
}
