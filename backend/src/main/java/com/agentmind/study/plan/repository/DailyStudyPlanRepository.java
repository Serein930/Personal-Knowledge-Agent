package com.agentmind.study.plan.repository;

import com.agentmind.study.plan.model.DailyStudyPlan;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import com.agentmind.study.plan.model.DailyStudyTask;
import com.agentmind.study.plan.model.DailyStudyTaskEvent;

/**
 * 每日学习计划存储端口。
 */
public interface DailyStudyPlanRepository {

    DailyStudyPlan saveOrUpdate(DailyStudyPlan plan, List<DailyStudyTask> tasks);

    Optional<DailyStudyPlan> findByScopeAndDate(Long ownerUserId, Long workspaceId, LocalDate planDate);

    List<DailyStudyTask> findTasksByScopeAndPlanId(Long ownerUserId, Long workspaceId, Long planId);

    Optional<DailyStudyTask> findTaskByScopeAndId(Long ownerUserId, Long workspaceId, Long taskId);

    /** 使用预期版本条件更新任务，版本冲突时返回空。 */
    Optional<DailyStudyTask> updateTask(DailyStudyTask task, long expectedVersion);

    DailyStudyTaskEvent saveTaskEvent(DailyStudyTaskEvent event);

    List<DailyStudyTaskEvent> findTaskEvents(Long ownerUserId, Long workspaceId, Long taskId);

    /** 后台补偿只扫描到期或逾期的待执行任务。 */
    List<DailyStudyTask> findPendingTasksScheduledOnOrBefore(LocalDate date, int limit);
}
