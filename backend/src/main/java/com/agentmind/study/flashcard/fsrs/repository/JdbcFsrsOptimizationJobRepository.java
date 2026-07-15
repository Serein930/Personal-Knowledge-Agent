package com.agentmind.study.flashcard.fsrs.repository;

import com.agentmind.study.flashcard.fsrs.model.FsrsOptimizationJob;
import com.agentmind.study.flashcard.fsrs.model.FsrsOptimizationJobStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** FSRS 参数优化任务 PostgreSQL 适配器。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.agent.persistence", name = "store", havingValue = "jdbc")
public class JdbcFsrsOptimizationJobRepository implements FsrsOptimizationJobRepository {

    private static final String COLUMNS = "id, owner_user_id, status, review_count, observed_lapse_rate, "
            + "previous_desired_retention, recommended_desired_retention, applied, message, created_at, completed_at";

    private final JdbcTemplate jdbcTemplate;

    public JdbcFsrsOptimizationJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public FsrsOptimizationJob save(FsrsOptimizationJob job) {
        if (job.id() == null) {
            return jdbcTemplate.query("""
                            insert into fsrs_parameter_optimization_jobs (
                                owner_user_id, status, review_count, observed_lapse_rate,
                                previous_desired_retention, recommended_desired_retention,
                                applied, message, created_at, completed_at
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning %s
                            """.formatted(COLUMNS), this::mapJob,
                    job.ownerUserId(), job.status().name(), job.reviewCount(), job.observedLapseRate(),
                    job.previousDesiredRetention(), job.recommendedDesiredRetention(), job.applied(),
                    job.message(), job.createdAt(), job.completedAt()).stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("创建 FSRS 优化任务失败"));
        }
        return jdbcTemplate.query("""
                        update fsrs_parameter_optimization_jobs
                        set status = ?, review_count = ?, observed_lapse_rate = ?,
                            previous_desired_retention = ?, recommended_desired_retention = ?,
                            applied = ?, message = ?, completed_at = ?
                        where id = ? and owner_user_id = ? returning %s
                        """.formatted(COLUMNS), this::mapJob,
                job.status().name(), job.reviewCount(), job.observedLapseRate(),
                job.previousDesiredRetention(), job.recommendedDesiredRetention(), job.applied(),
                job.message(), job.completedAt(), job.id(), job.ownerUserId()).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("更新 FSRS 优化任务失败"));
    }

    @Override
    public List<FsrsOptimizationJob> findByOwnerUserId(Long ownerUserId, int offset, int limit) {
        return jdbcTemplate.query("select " + COLUMNS + " from fsrs_parameter_optimization_jobs "
                        + "where owner_user_id = ? order by created_at desc, id desc offset ? limit ?",
                this::mapJob, ownerUserId, offset, limit);
    }

    @Override
    public long countByOwnerUserId(Long ownerUserId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from fsrs_parameter_optimization_jobs where owner_user_id = ?",
                Long.class, ownerUserId
        );
        return count == null ? 0 : count;
    }

    private FsrsOptimizationJob mapJob(ResultSet resultSet, int rowNumber) throws SQLException {
        return new FsrsOptimizationJob(
                resultSet.getLong("id"), resultSet.getLong("owner_user_id"),
                FsrsOptimizationJobStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("review_count"), resultSet.getDouble("observed_lapse_rate"),
                resultSet.getDouble("previous_desired_retention"),
                resultSet.getDouble("recommended_desired_retention"),
                resultSet.getBoolean("applied"), resultSet.getString("message"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("completed_at", OffsetDateTime.class)
        );
    }
}
