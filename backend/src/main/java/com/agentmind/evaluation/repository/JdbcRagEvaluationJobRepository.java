package com.agentmind.evaluation.repository;

import com.agentmind.evaluation.model.RagEvaluationCaseResult;
import com.agentmind.evaluation.model.RagEvaluationJob;
import com.agentmind.evaluation.model.RagEvaluationJobStatus;
import com.agentmind.evaluation.model.RagEvaluationMetrics;
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

/** 评估任务 PostgreSQL 适配器。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.evaluation", name = "store", havingValue = "jdbc")
public class JdbcRagEvaluationJobRepository implements RagEvaluationJobRepository {

    private static final String COLUMNS = "id, owner_user_id, workspace_id, dataset_id, dataset_version, status, "
            + "retrieval_strategy, top_k, prompt_version, model_name, baseline_job_id, metrics::text, "
            + "case_results::text, failure_reason, created_at, started_at, completed_at";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcRagEvaluationJobRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public RagEvaluationJob save(RagEvaluationJob job) {
        if (job.id() == null) {
            return jdbcTemplate.query("""
                            insert into rag_evaluation_jobs (
                                owner_user_id, workspace_id, dataset_id, dataset_version, status,
                                retrieval_strategy, top_k, prompt_version, model_name, baseline_job_id,
                                metrics, case_results, failure_reason, created_at, started_at, completed_at
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?, ?, ?)
                            returning %s
                            """.formatted(COLUMNS), this::mapJob,
                    job.ownerUserId(), job.workspaceId(), job.datasetId(), job.datasetVersion(), job.status().name(),
                    job.retrievalStrategy(), job.topK(), job.promptVersion(), job.modelName(), job.baselineJobId(),
                    writeJson(job.metrics()), writeJson(job.caseResults()), safe(job.failureReason()),
                    job.createdAt(), job.startedAt(), job.completedAt()).stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("创建评估任务失败"));
        }
        return jdbcTemplate.query("""
                        update rag_evaluation_jobs
                        set status = ?, baseline_job_id = ?, metrics = cast(? as jsonb),
                            case_results = cast(? as jsonb), failure_reason = ?, completed_at = ?
                        where id = ? and owner_user_id = ? and workspace_id = ?
                        returning %s
                        """.formatted(COLUMNS), this::mapJob,
                job.status().name(), job.baselineJobId(), writeJson(job.metrics()), writeJson(job.caseResults()),
                safe(job.failureReason()), job.completedAt(), job.id(), job.ownerUserId(), job.workspaceId())
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("更新评估任务失败"));
    }

    @Override
    public Optional<RagEvaluationJob> findByScopeAndId(Long ownerUserId, Long workspaceId, Long jobId) {
        return jdbcTemplate.query(
                "select " + COLUMNS + " from rag_evaluation_jobs "
                        + "where owner_user_id = ? and workspace_id = ? and id = ?",
                this::mapJob, ownerUserId, workspaceId, jobId
        ).stream().findFirst();
    }

    @Override
    public Optional<RagEvaluationJob> findLatestSuccessful(
            Long ownerUserId,
            Long workspaceId,
            Long datasetId,
            int datasetVersion
    ) {
        return jdbcTemplate.query(
                "select " + COLUMNS + " from rag_evaluation_jobs "
                        + "where owner_user_id = ? and workspace_id = ? and dataset_id = ? "
                        + "and dataset_version = ? and status = 'SUCCEEDED' "
                        + "order by completed_at desc, id desc limit 1",
                this::mapJob, ownerUserId, workspaceId, datasetId, datasetVersion
        ).stream().findFirst();
    }

    @Override
    public Optional<RagEvaluationJob> findLatestSuccessfulByScope(Long ownerUserId, Long workspaceId) {
        return jdbcTemplate.query(
                "select " + COLUMNS + " from rag_evaluation_jobs "
                        + "where owner_user_id = ? and workspace_id = ? and status = 'SUCCEEDED' "
                        + "order by completed_at desc, id desc limit 1",
                this::mapJob, ownerUserId, workspaceId
        ).stream().findFirst();
    }

    @Override
    public List<RagEvaluationJob> findByScope(Long ownerUserId, Long workspaceId, int offset, int limit) {
        return jdbcTemplate.query(
                "select " + COLUMNS + " from rag_evaluation_jobs "
                        + "where owner_user_id = ? and workspace_id = ? "
                        + "order by created_at desc, id desc offset ? limit ?",
                this::mapJob, ownerUserId, workspaceId, offset, limit
        );
    }

    @Override
    public long countByScope(Long ownerUserId, Long workspaceId, RagEvaluationJobStatus status) {
        Long count;
        if (status == null) {
            count = jdbcTemplate.queryForObject(
                    "select count(*) from rag_evaluation_jobs where owner_user_id = ? and workspace_id = ?",
                    Long.class, ownerUserId, workspaceId
            );
        } else {
            count = jdbcTemplate.queryForObject(
                    "select count(*) from rag_evaluation_jobs "
                            + "where owner_user_id = ? and workspace_id = ? and status = ?",
                    Long.class, ownerUserId, workspaceId, status.name()
            );
        }
        return count == null ? 0 : count;
    }

    private RagEvaluationJob mapJob(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RagEvaluationJob(
                resultSet.getLong("id"), resultSet.getLong("owner_user_id"),
                resultSet.getLong("workspace_id"), resultSet.getLong("dataset_id"),
                resultSet.getInt("dataset_version"),
                RagEvaluationJobStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("retrieval_strategy"), resultSet.getInt("top_k"),
                resultSet.getString("prompt_version"), resultSet.getString("model_name"),
                nullableLong(resultSet, "baseline_job_id"), readMetrics(resultSet.getString("metrics")),
                readResults(resultSet.getString("case_results")), resultSet.getString("failure_reason"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("started_at", OffsetDateTime.class),
                resultSet.getObject("completed_at", OffsetDateTime.class)
        );
    }

    private RagEvaluationMetrics readMetrics(String json) throws SQLException {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, RagEvaluationMetrics.class);
        } catch (JsonProcessingException exception) {
            throw new SQLException("读取评估聚合指标失败", exception);
        }
    }

    private List<RagEvaluationCaseResult> readResults(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new SQLException("读取评估逐题结果失败", exception);
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化评估任务快照失败", exception);
        }
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
