package com.agentmind.study.plan.repository;

import com.agentmind.study.plan.model.DailyStudyPlan;
import com.agentmind.study.plan.model.DailyStudyTask;
import com.agentmind.study.plan.model.DailyStudyTaskAction;
import com.agentmind.study.plan.model.DailyStudyTaskEvent;
import com.agentmind.study.plan.model.DailyStudyTaskPriority;
import com.agentmind.study.plan.model.DailyStudyTaskStatus;
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
        Integer taskCount = jdbcTemplate.queryForObject(
                "select count(*) from daily_study_tasks where owner_user_id = ? and workspace_id = ? and plan_id = ?",
                Integer.class, stored.ownerUserId(), stored.workspaceId(), stored.id()
        );
        // 更新每日目标时保留已生成任务及其执行历史；只有新计划才写入初始任务快照。
        if (taskCount == null || taskCount == 0) {
            for (DailyStudyTask task : tasks) {
                Long taskId = jdbcTemplate.queryForObject("""
                        insert into daily_study_tasks (
                            plan_id, owner_user_id, workspace_id, task_type, priority, status,
                            scheduled_date, topic, source_document_id, target_card_count, reason,
                            feedback_score, feedback_comment, completed_at, skipped_at, version,
                            created_at, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id
                        """, Long.class,
                        stored.id(), task.ownerUserId(), task.workspaceId(), task.type().name(),
                        task.priority().name(), task.status().name(), task.scheduledDate(), task.topic(),
                        task.sourceDocumentId(), task.targetCardCount(), task.reason(), task.feedbackScore(),
                        task.feedbackComment(), task.completedAt(), task.skippedAt(), task.version(),
                        task.createdAt(), task.updatedAt());
                for (Long flashcardId : task.flashcardIds()) {
                    jdbcTemplate.update("""
                            insert into daily_study_task_cards (
                                task_id, owner_user_id, workspace_id, flashcard_id
                            ) values (?, ?, ?, ?)
                            """, taskId, task.ownerUserId(), task.workspaceId(), flashcardId);
                }
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
                        select id, plan_id, owner_user_id, workspace_id, task_type, priority, status,
                               scheduled_date, topic, source_document_id, target_card_count, reason,
                               feedback_score, feedback_comment, completed_at, skipped_at, version,
                               created_at, updated_at
                        from daily_study_tasks
                        where owner_user_id = ? and workspace_id = ? and plan_id = ?
                        order by case priority when 'HIGH' then 1 when 'MEDIUM' then 2 else 3 end, id
                        """, (resultSet, rowNumber) -> new DailyStudyTask(
                        resultSet.getLong("id"), resultSet.getLong("plan_id"),
                        resultSet.getLong("owner_user_id"), resultSet.getLong("workspace_id"),
                        DailyStudyTaskType.valueOf(resultSet.getString("task_type")),
                        DailyStudyTaskPriority.valueOf(resultSet.getString("priority")),
                        DailyStudyTaskStatus.valueOf(resultSet.getString("status")),
                        resultSet.getObject("scheduled_date", LocalDate.class),
                        resultSet.getString("topic"), nullableLong(resultSet, "source_document_id"),
                        resultSet.getInt("target_card_count"), resultSet.getString("reason"),
                        findTaskFlashcardIds(resultSet.getLong("id"), ownerUserId, workspaceId),
                        nullableInteger(resultSet, "feedback_score"), resultSet.getString("feedback_comment"),
                        resultSet.getObject("completed_at", OffsetDateTime.class),
                        resultSet.getObject("skipped_at", OffsetDateTime.class), resultSet.getLong("version"),
                        resultSet.getObject("created_at", OffsetDateTime.class),
                        resultSet.getObject("updated_at", OffsetDateTime.class)
                ), ownerUserId, workspaceId, planId);
    }

    @Override
    public Optional<DailyStudyTask> findTaskByScopeAndId(Long ownerUserId, Long workspaceId, Long taskId) {
        return queryTasks("where owner_user_id = ? and workspace_id = ? and id = ?", ownerUserId, workspaceId, taskId)
                .stream().findFirst();
    }

    @Override
    public Optional<DailyStudyTask> updateTask(DailyStudyTask task, long expectedVersion) {
        return jdbcTemplate.query("""
                        update daily_study_tasks
                        set status = ?, scheduled_date = ?, feedback_score = ?, feedback_comment = ?,
                            completed_at = ?, skipped_at = ?, version = ?, updated_at = ?
                        where id = ? and owner_user_id = ? and workspace_id = ? and version = ?
                        returning id, plan_id, owner_user_id, workspace_id, task_type, priority, status,
                                  scheduled_date, topic, source_document_id, target_card_count, reason,
                                  feedback_score, feedback_comment, completed_at, skipped_at, version,
                                  created_at, updated_at
                        """, (resultSet, rowNumber) -> mapTask(resultSet),
                task.status().name(), task.scheduledDate(), task.feedbackScore(), task.feedbackComment(),
                task.completedAt(), task.skippedAt(), task.version(), task.updatedAt(), task.id(),
                task.ownerUserId(), task.workspaceId(), expectedVersion).stream().findFirst();
    }

    @Override
    public DailyStudyTaskEvent saveTaskEvent(DailyStudyTaskEvent event) {
        return jdbcTemplate.query("""
                        insert into daily_study_task_events (
                            task_id, owner_user_id, workspace_id, action, previous_status, next_status,
                            previous_scheduled_date, next_scheduled_date, feedback_score, comment, created_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        returning id, task_id, owner_user_id, workspace_id, action, previous_status, next_status,
                                  previous_scheduled_date, next_scheduled_date, feedback_score, comment, created_at
                        """, (resultSet, rowNumber) -> mapEvent(resultSet),
                event.taskId(), event.ownerUserId(), event.workspaceId(), event.action().name(),
                event.previousStatus().name(), event.nextStatus().name(), event.previousScheduledDate(),
                event.nextScheduledDate(), event.feedbackScore(), event.comment(), event.createdAt())
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("保存学习任务事件失败"));
    }

    @Override
    public List<DailyStudyTaskEvent> findTaskEvents(Long ownerUserId, Long workspaceId, Long taskId) {
        return jdbcTemplate.query("""
                        select id, task_id, owner_user_id, workspace_id, action, previous_status, next_status,
                               previous_scheduled_date, next_scheduled_date, feedback_score, comment, created_at
                        from daily_study_task_events
                        where owner_user_id = ? and workspace_id = ? and task_id = ?
                        order by created_at, id
                        """, (resultSet, rowNumber) -> mapEvent(resultSet), ownerUserId, workspaceId, taskId);
    }

    @Override
    public List<DailyStudyTask> findPendingTasksScheduledOnOrBefore(LocalDate date, int limit) {
        return queryTasks(
                "where status = 'PENDING' and scheduled_date <= ? order by scheduled_date, id limit ?",
                date, limit
        );
    }

    private List<DailyStudyTask> queryTasks(String suffix, Object... arguments) {
        return jdbcTemplate.query("""
                        select id, plan_id, owner_user_id, workspace_id, task_type, priority, status,
                               scheduled_date, topic, source_document_id, target_card_count, reason,
                               feedback_score, feedback_comment, completed_at, skipped_at, version,
                               created_at, updated_at
                        from daily_study_tasks
                        """ + suffix, (resultSet, rowNumber) -> mapTask(resultSet), arguments);
    }

    private DailyStudyTask mapTask(ResultSet resultSet) throws SQLException {
        long taskId = resultSet.getLong("id");
        long ownerUserId = resultSet.getLong("owner_user_id");
        long workspaceId = resultSet.getLong("workspace_id");
        return new DailyStudyTask(
                taskId, resultSet.getLong("plan_id"), ownerUserId, workspaceId,
                DailyStudyTaskType.valueOf(resultSet.getString("task_type")),
                DailyStudyTaskPriority.valueOf(resultSet.getString("priority")),
                DailyStudyTaskStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("scheduled_date", LocalDate.class), resultSet.getString("topic"),
                nullableLong(resultSet, "source_document_id"), resultSet.getInt("target_card_count"),
                resultSet.getString("reason"), findTaskFlashcardIds(taskId, ownerUserId, workspaceId),
                nullableInteger(resultSet, "feedback_score"), resultSet.getString("feedback_comment"),
                resultSet.getObject("completed_at", OffsetDateTime.class),
                resultSet.getObject("skipped_at", OffsetDateTime.class), resultSet.getLong("version"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private DailyStudyTaskEvent mapEvent(ResultSet resultSet) throws SQLException {
        return new DailyStudyTaskEvent(
                resultSet.getLong("id"), resultSet.getLong("task_id"),
                resultSet.getLong("owner_user_id"), resultSet.getLong("workspace_id"),
                DailyStudyTaskAction.valueOf(resultSet.getString("action")),
                DailyStudyTaskStatus.valueOf(resultSet.getString("previous_status")),
                DailyStudyTaskStatus.valueOf(resultSet.getString("next_status")),
                resultSet.getObject("previous_scheduled_date", LocalDate.class),
                resultSet.getObject("next_scheduled_date", LocalDate.class),
                nullableInteger(resultSet, "feedback_score"), resultSet.getString("comment"),
                resultSet.getObject("created_at", OffsetDateTime.class)
        );
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

    private Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }
}
