package com.agentmind.study.plan.repository;

import com.agentmind.study.plan.model.DailyStudyPlan;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 每日学习计划 PostgreSQL 适配器。
 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.agent.persistence", name = "store", havingValue = "jdbc")
public class JdbcDailyStudyPlanRepository implements DailyStudyPlanRepository {

    private static final String COLUMNS = "id, owner_user_id, workspace_id, plan_date, daily_review_target, "
            + "due_card_snapshot, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<DailyStudyPlan> rowMapper = this::mapPlan;

    public JdbcDailyStudyPlanRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public DailyStudyPlan saveOrUpdate(DailyStudyPlan plan) {
        return jdbcTemplate.query("""
                        insert into daily_study_plans (
                            owner_user_id, workspace_id, plan_date, daily_review_target,
                            due_card_snapshot, created_at, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?)
                        on conflict (owner_user_id, workspace_id, plan_date) do update
                        set daily_review_target = excluded.daily_review_target,
                            updated_at = excluded.updated_at
                        returning %s
                        """.formatted(COLUMNS),
                        rowMapper,
                        plan.ownerUserId(), plan.workspaceId(), plan.planDate(), plan.dailyReviewTarget(),
                        plan.dueCardSnapshot(), plan.createdAt(), plan.updatedAt()
                ).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("保存每日学习计划失败"));
    }

    @Override
    public Optional<DailyStudyPlan> findByScopeAndDate(
            Long ownerUserId,
            Long workspaceId,
            LocalDate planDate
    ) {
        return jdbcTemplate.query(
                        "select " + COLUMNS + " from daily_study_plans "
                                + "where owner_user_id = ? and workspace_id = ? and plan_date = ?",
                        rowMapper,
                        ownerUserId,
                        workspaceId,
                        planDate
                ).stream()
                .findFirst();
    }

    private DailyStudyPlan mapPlan(ResultSet resultSet, int rowNumber) throws SQLException {
        return new DailyStudyPlan(
                resultSet.getLong("id"), resultSet.getLong("owner_user_id"),
                resultSet.getLong("workspace_id"), resultSet.getObject("plan_date", LocalDate.class),
                resultSet.getInt("daily_review_target"), resultSet.getInt("due_card_snapshot"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
