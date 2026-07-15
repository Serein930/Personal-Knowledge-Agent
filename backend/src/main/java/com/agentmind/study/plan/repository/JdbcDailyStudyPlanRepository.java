package com.agentmind.study.plan.repository;

import com.agentmind.study.plan.model.DailyStudyPlan;
import com.agentmind.study.plan.model.DailyStudyTask;
import com.agentmind.study.plan.model.DailyStudyTaskPriority;
import com.agentmind.study.plan.model.DailyStudyTaskType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public DailyStudyPlan saveOrUpdate(DailyStudyPlan plan, List<DailyStudyTask> tasks) {
        DailyStudyPlan stored = jdbcTemplate.query("""
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
        jdbcTemplate.update(
                "delete from daily_study_tasks where owner_user_id = ? and workspace_id = ? and plan_id = ?",
                stored.ownerUserId(), stored.workspaceId(), stored.id()
        );
        for (DailyStudyTask task : tasks) {
            Long taskId = jdbcTemplate.queryForObject("""
                    insert into daily_study_tasks (
                        plan_id, owner_user_id, workspace_id, task_type, priority, topic,
                        source_document_id, target_card_count, reason, created_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id
                    """, Long.class,
                    stored.id(), task.ownerUserId(), task.workspaceId(), task.type().name(),
                    task.priority().name(), task.topic(), task.sourceDocumentId(),
                    task.targetCardCount(), task.reason(), task.createdAt());
            for (Long flashcardId : task.flashcardIds()) {
                jdbcTemplate.update("""
                        insert into daily_study_task_cards (
                            task_id, owner_user_id, workspace_id, flashcard_id
                        ) values (?, ?, ?, ?)
                        """, taskId, task.ownerUserId(), task.workspaceId(), flashcardId);
            }
        }
        return stored;
    }

    @Override
    public List<DailyStudyTask> findTasksByScopeAndPlanId(
            Long ownerUserId,
            Long workspaceId,
            Long planId
    ) {
        return jdbcTemplate.query("""
                        select id, plan_id, owner_user_id, workspace_id, task_type, priority,
                               topic, source_document_id, target_card_count, reason, created_at
                        from daily_study_tasks
                        where owner_user_id = ? and workspace_id = ? and plan_id = ?
                        order by case priority when 'HIGH' then 1 when 'MEDIUM' then 2 else 3 end, id
                        """, (resultSet, rowNumber) -> new DailyStudyTask(
                        resultSet.getLong("id"), resultSet.getLong("plan_id"),
                        resultSet.getLong("owner_user_id"), resultSet.getLong("workspace_id"),
                        DailyStudyTaskType.valueOf(resultSet.getString("task_type")),
                        DailyStudyTaskPriority.valueOf(resultSet.getString("priority")),
                        resultSet.getString("topic"), nullableLong(resultSet, "source_document_id"),
                        resultSet.getInt("target_card_count"), resultSet.getString("reason"),
                        findTaskFlashcardIds(resultSet.getLong("id"), ownerUserId, workspaceId),
                        resultSet.getObject("created_at", OffsetDateTime.class)
                ), ownerUserId, workspaceId, planId);
    }

    private List<Long> findTaskFlashcardIds(Long taskId, Long ownerUserId, Long workspaceId) {
        return jdbcTemplate.queryForList(
                "select flashcard_id from daily_study_task_cards "
                        + "where task_id = ? and owner_user_id = ? and workspace_id = ? order by flashcard_id",
                Long.class, taskId, ownerUserId, workspaceId
        );
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

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }
}
