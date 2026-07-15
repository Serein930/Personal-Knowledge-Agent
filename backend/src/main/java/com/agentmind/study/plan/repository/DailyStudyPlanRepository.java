package com.agentmind.study.plan.repository;

import com.agentmind.study.plan.model.DailyStudyPlan;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 每日学习计划存储端口。
 */
public interface DailyStudyPlanRepository {

    DailyStudyPlan saveOrUpdate(DailyStudyPlan plan);

    Optional<DailyStudyPlan> findByScopeAndDate(Long ownerUserId, Long workspaceId, LocalDate planDate);
}
