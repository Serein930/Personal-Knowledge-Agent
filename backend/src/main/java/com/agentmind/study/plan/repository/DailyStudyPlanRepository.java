package com.agentmind.study.plan.repository;

import com.agentmind.study.plan.model.DailyStudyPlan;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import com.agentmind.study.plan.model.DailyStudyTask;

/**
 * 每日学习计划存储端口。
 */
public interface DailyStudyPlanRepository {

    DailyStudyPlan saveOrUpdate(DailyStudyPlan plan, List<DailyStudyTask> tasks);

    Optional<DailyStudyPlan> findByScopeAndDate(Long ownerUserId, Long workspaceId, LocalDate planDate);

    List<DailyStudyTask> findTasksByScopeAndPlanId(Long ownerUserId, Long workspaceId, Long planId);
}
