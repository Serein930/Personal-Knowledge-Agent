package com.agentmind.ingestion.repository;

import com.agentmind.ingestion.model.IngestionTask;
import com.agentmind.ingestion.model.IngestionTaskStatus;
import com.agentmind.ingestion.model.IngestionTaskType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** PostgreSQL 摄取任务仓储，任务状态在应用重启后仍可继续查询。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.core.persistence", name = "store", havingValue = "jdbc")
public class JdbcIngestionTaskRepository implements IngestionTaskRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcIngestionTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public IngestionTask create(Long ownerUserId, Long workspaceId, Long documentId,
            IngestionTaskType taskType, IngestionTaskStatus status, int progress, String source) {
        OffsetDateTime now = OffsetDateTime.now();
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    insert into ingestion_task (
                        owner_user_id, workspace_id, document_id, task_type, status, progress,
                        source, created_at, updated_at, started_at, finished_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, ownerUserId);
            statement.setLong(2, workspaceId);
            statement.setLong(3, documentId);
            statement.setString(4, taskType.name());
            statement.setString(5, status.name());
            statement.setInt(6, progress);
            statement.setString(7, source);
            statement.setObject(8, now);
            statement.setObject(9, now);
            statement.setObject(10, status == IngestionTaskStatus.RUNNING ? now : null);
            statement.setObject(11, isFinished(status) ? now : null);
            return statement;
        }, keyHolder);
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("创建摄取任务后未返回主键");
        }
        return findByWorkspaceIdAndId(workspaceId, id.longValue()).orElseThrow();
    }

    @Override
    public void update(Long taskId, IngestionTaskStatus status, int progress, String source, String errorMessage) {
        jdbcTemplate.update("""
                update ingestion_task
                set status = ?, progress = ?, source = ?, error_message = ?, updated_at = ?,
                    started_at = case when ? = 'RUNNING' then coalesce(started_at, ?) else started_at end,
                    finished_at = case when ? in ('SUCCEEDED', 'FAILED') then ? else finished_at end
                where id = ?
                """, status.name(), progress, source, errorMessage, OffsetDateTime.now(), status.name(),
                OffsetDateTime.now(), status.name(), OffsetDateTime.now(), taskId);
    }

    @Override
    public Optional<IngestionTask> findByWorkspaceIdAndId(Long workspaceId, Long taskId) {
        return jdbcTemplate.query("""
                select * from ingestion_task where workspace_id = ? and id = ?
                """, this::mapTask, workspaceId, taskId).stream().findFirst();
    }

    private IngestionTask mapTask(ResultSet resultSet, int rowNumber) throws SQLException {
        return new IngestionTask(resultSet.getLong("id"), resultSet.getLong("owner_user_id"),
                resultSet.getLong("workspace_id"), resultSet.getLong("document_id"),
                IngestionTaskType.valueOf(resultSet.getString("task_type")),
                IngestionTaskStatus.valueOf(resultSet.getString("status")), resultSet.getInt("progress"),
                resultSet.getString("source"), resultSet.getString("error_message"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class),
                resultSet.getObject("started_at", OffsetDateTime.class),
                resultSet.getObject("finished_at", OffsetDateTime.class));
    }

    private boolean isFinished(IngestionTaskStatus status) {
        return status == IngestionTaskStatus.SUCCEEDED || status == IngestionTaskStatus.FAILED;
    }
}
