package com.agentmind.study.plan.repository;

import com.agentmind.study.plan.model.DailyStudyPlan;
import com.agentmind.study.plan.model.DailyStudyTask;
import com.agentmind.study.plan.model.DailyStudyTaskEvent;
import com.agentmind.study.plan.model.DailyStudyTaskStatus;
import java.time.LocalDate;
import java.util.List;
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
    private final AtomicLong taskIdGenerator = new AtomicLong(1);
    private final AtomicLong eventIdGenerator = new AtomicLong(1);
    private final ConcurrentHashMap<String, DailyStudyPlan> plans = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<DailyStudyTask>> tasksByPlanId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, DailyStudyTask> tasksById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, List<DailyStudyTaskEvent>> eventsByTaskId = new ConcurrentHashMap<>();

    @Override
    public synchronized DailyStudyPlan saveOrUpdate(DailyStudyPlan plan, List<DailyStudyTask> tasks) {
        String key = key(plan.ownerUserId(), plan.workspaceId(), plan.planDate());
        DailyStudyPlan existing = plans.get(key);
        DailyStudyPlan stored = new DailyStudyPlan(
                existing == null ? idGenerator.getAndIncrement() : existing.id(),
                plan.ownerUserId(), plan.workspaceId(), plan.planDate(), plan.dailyReviewTarget(),
                existing == null ? plan.dueCardSnapshot() : existing.dueCardSnapshot(),
                existing == null ? plan.createdAt() : existing.createdAt(), plan.updatedAt()
        );
        plans.put(key, stored);
        if (!tasksByPlanId.containsKey(stored.id())) {
            List<DailyStudyTask> storedTasks = tasks.stream()
                    .map(task -> task.withIdentity(taskIdGenerator.getAndIncrement(), stored.id()))
                    .toList();
            tasksByPlanId.put(stored.id(), storedTasks);
            storedTasks.forEach(task -> tasksById.put(task.id(), task));
        }
        return stored;
    }

    @Override
    public List<DailyStudyTask> findTasksByScopeAndPlanId(
            Long ownerUserId,
            Long workspaceId,
            Long planId
    ) {
        return tasksByPlanId.getOrDefault(planId, List.of()).stream()
                .map(task -> tasksById.getOrDefault(task.id(), task))
                .filter(task -> ownerUserId.equals(task.ownerUserId()))
                .filter(task -> workspaceId.equals(task.workspaceId()))
                .toList();
    }

    @Override
    public Optional<DailyStudyTask> findTaskByScopeAndId(Long ownerUserId, Long workspaceId, Long taskId) {
        return Optional.ofNullable(tasksById.get(taskId))
                .filter(task -> ownerUserId.equals(task.ownerUserId()))
                .filter(task -> workspaceId.equals(task.workspaceId()));
    }

    @Override
    public synchronized Optional<DailyStudyTask> updateTask(DailyStudyTask task, long expectedVersion) {
        DailyStudyTask current = tasksById.get(task.id());
        if (current == null || current.version() != expectedVersion
                || !current.ownerUserId().equals(task.ownerUserId())
                || !current.workspaceId().equals(task.workspaceId())) {
            return Optional.empty();
        }
        tasksById.put(task.id(), task);
        return Optional.of(task);
    }

    @Override
    public synchronized DailyStudyTaskEvent saveTaskEvent(DailyStudyTaskEvent event) {
        DailyStudyTaskEvent stored = event.id() == null ? event.withId(eventIdGenerator.getAndIncrement()) : event;
        eventsByTaskId.compute(stored.taskId(), (ignored, current) -> {
            java.util.ArrayList<DailyStudyTaskEvent> next = new java.util.ArrayList<>(
                    current == null ? List.of() : current
            );
            next.add(stored);
            return List.copyOf(next);
        });
        return stored;
    }

    @Override
    public List<DailyStudyTaskEvent> findTaskEvents(Long ownerUserId, Long workspaceId, Long taskId) {
        return eventsByTaskId.getOrDefault(taskId, List.of()).stream()
                .filter(event -> ownerUserId.equals(event.ownerUserId()))
                .filter(event -> workspaceId.equals(event.workspaceId()))
                .sorted(java.util.Comparator.comparing(DailyStudyTaskEvent::createdAt)
                        .thenComparing(DailyStudyTaskEvent::id))
                .toList();
    }

    @Override
    public List<DailyStudyTask> findPendingTasksScheduledOnOrBefore(LocalDate date, int limit) {
        return tasksById.values().stream()
                .filter(task -> task.status() == DailyStudyTaskStatus.PENDING)
                .filter(task -> !task.scheduledDate().isAfter(date))
                .sorted(java.util.Comparator.comparing(DailyStudyTask::scheduledDate)
                        .thenComparing(DailyStudyTask::id))
                .limit(limit)
                .toList();
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
