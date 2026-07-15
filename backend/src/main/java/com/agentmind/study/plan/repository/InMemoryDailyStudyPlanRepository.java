package com.agentmind.study.plan.repository;

import com.agentmind.study.plan.model.DailyStudyPlan;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * 每日学习计划内存适配器。
 */
@Repository
@ConditionalOnProperty(
        prefix = "agentmind.agent.persistence",
        name = "store",
        havingValue = "memory",
        matchIfMissing = true
)
public class InMemoryDailyStudyPlanRepository implements DailyStudyPlanRepository {

    private final AtomicLong idGenerator = new AtomicLong(1);
    private final ConcurrentHashMap<String, DailyStudyPlan> plans = new ConcurrentHashMap<>();

    @Override
    public synchronized DailyStudyPlan saveOrUpdate(DailyStudyPlan plan) {
        String key = key(plan.ownerUserId(), plan.workspaceId(), plan.planDate());
        DailyStudyPlan existing = plans.get(key);
        DailyStudyPlan stored = new DailyStudyPlan(
                existing == null ? idGenerator.getAndIncrement() : existing.id(),
                plan.ownerUserId(), plan.workspaceId(), plan.planDate(), plan.dailyReviewTarget(),
                existing == null ? plan.dueCardSnapshot() : existing.dueCardSnapshot(),
                existing == null ? plan.createdAt() : existing.createdAt(), plan.updatedAt()
        );
        plans.put(key, stored);
        return stored;
    }

    @Override
    public Optional<DailyStudyPlan> findByScopeAndDate(
            Long ownerUserId,
            Long workspaceId,
            LocalDate planDate
    ) {
        return Optional.ofNullable(plans.get(key(ownerUserId, workspaceId, planDate)));
    }

    private String key(Long ownerUserId, Long workspaceId, LocalDate planDate) {
        return ownerUserId + ":" + workspaceId + ":" + planDate;
    }
}
