package com.agentmind.study.flashcard.fsrs.repository;

import com.agentmind.study.flashcard.fsrs.model.FsrsOptimizationJob;
import com.agentmind.study.flashcard.fsrs.model.FsrsOptimizationJobStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** FSRS 权重拟合任务 PostgreSQL 适配器。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.agent.persistence", name = "store", havingValue = "jdbc")
public class JdbcFsrsOptimizationJobRepository implements FsrsOptimizationJobRepository {

    private static final String COLUMNS = "id, owner_user_id, status, review_count, effective_observation_count, "
            + "observed_lapse_rate, previous_parameters::text, recommended_parameters::text, "
            + "previous_desired_retention, recommended_desired_retention, training_loss_before, "
            + "training_loss_after, validation_loss_before, validation_loss_after, accepted, applied, "
            + "applied_version, message, created_at, completed_at";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcFsrsOptimizationJobRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public FsrsOptimizationJob save(FsrsOptimizationJob job) {
        if (job.id() == null) {
            return jdbcTemplate.query("""
                            insert into fsrs_parameter_optimization_jobs (
                                owner_user_id, status, review_count, effective_observation_count,
                                observed_lapse_rate, previous_parameters, recommended_parameters,
                                previous_desired_retention, recommended_desired_retention,
                                training_loss_before, training_loss_after,
                                validation_loss_before, validation_loss_after,
                                accepted, applied, applied_version, message, created_at, completed_at
                            ) values (?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            returning %s
                            """.formatted(COLUMNS), this::mapJob,
                    values(job)).stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("创建 FSRS 优化任务失败"));
        }
        return jdbcTemplate.query("""
                        update fsrs_parameter_optimization_jobs
                        set status = ?, review_count = ?, effective_observation_count = ?,
                            observed_lapse_rate = ?, previous_parameters = cast(? as jsonb),
                            recommended_parameters = cast(? as jsonb), previous_desired_retention = ?,
                            recommended_desired_retention = ?, training_loss_before = ?, training_loss_after = ?,
                            validation_loss_before = ?, validation_loss_after = ?, accepted = ?, applied = ?,
                            applied_version = ?, message = ?, completed_at = ?
                        where id = ? and owner_user_id = ? returning %s
                        """.formatted(COLUMNS), this::mapJob,
                job.status().name(), job.reviewCount(), job.effectiveObservationCount(), job.observedLapseRate(),
                writeParameters(job.previousParameters()), writeParameters(job.recommendedParameters()),
                job.previousDesiredRetention(), job.recommendedDesiredRetention(),
                job.trainingLossBefore(), job.trainingLossAfter(), job.validationLossBefore(), job.validationLossAfter(),
                job.accepted(), job.applied(), job.appliedVersion(), job.message(), job.completedAt(),
                job.id(), job.ownerUserId()).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("更新 FSRS 优化任务失败"));
    }

    private Object[] values(FsrsOptimizationJob job) {
        return new Object[]{
                job.ownerUserId(), job.status().name(), job.reviewCount(), job.effectiveObservationCount(),
                job.observedLapseRate(), writeParameters(job.previousParameters()),
                writeParameters(job.recommendedParameters()), job.previousDesiredRetention(),
                job.recommendedDesiredRetention(), job.trainingLossBefore(), job.trainingLossAfter(),
                job.validationLossBefore(), job.validationLossAfter(), job.accepted(), job.applied(),
                job.appliedVersion(), job.message(), job.createdAt(), job.completedAt()
        };
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

    @Override
    public Optional<FsrsOptimizationJob> findLatestByOwnerUserId(Long ownerUserId) {
        return jdbcTemplate.query(
                "select " + COLUMNS + " from fsrs_parameter_optimization_jobs "
                        + "where owner_user_id = ? order by created_at desc, id desc limit 1",
                this::mapJob, ownerUserId
        ).stream().findFirst();
    }

    private FsrsOptimizationJob mapJob(ResultSet resultSet, int rowNumber) throws SQLException {
        return new FsrsOptimizationJob(
                resultSet.getLong("id"), resultSet.getLong("owner_user_id"),
                FsrsOptimizationJobStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("review_count"), resultSet.getInt("effective_observation_count"),
                resultSet.getDouble("observed_lapse_rate"), readParameters(resultSet, "previous_parameters"),
                readParameters(resultSet, "recommended_parameters"),
                resultSet.getDouble("previous_desired_retention"),
                resultSet.getDouble("recommended_desired_retention"),
                resultSet.getDouble("training_loss_before"), resultSet.getDouble("training_loss_after"),
                resultSet.getDouble("validation_loss_before"), resultSet.getDouble("validation_loss_after"),
                resultSet.getBoolean("accepted"), resultSet.getBoolean("applied"),
                nullableLong(resultSet, "applied_version"), resultSet.getString("message"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("completed_at", OffsetDateTime.class)
        );
    }

    private List<Double> readParameters(ResultSet resultSet, String column) throws SQLException {
        try {
            return objectMapper.readValue(resultSet.getString(column), new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new SQLException("读取 FSRS 优化任务参数失败", exception);
        }
    }

    private String writeParameters(List<Double> parameters) {
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化 FSRS 优化任务参数失败", exception);
        }
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }
}
