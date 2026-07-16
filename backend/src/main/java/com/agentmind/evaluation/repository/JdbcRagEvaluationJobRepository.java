package com.agentmind.evaluation.repository;

import com.agentmind.evaluation.model.RagEvaluationCaseResult;
import com.agentmind.evaluation.model.RagEvaluationExperimentConfig;
import com.agentmind.evaluation.model.RagEvaluationJob;
import com.agentmind.evaluation.model.RagEvaluationJobStatus;
import com.agentmind.evaluation.model.RagEvaluationMetrics;
import com.agentmind.evaluation.model.RagEvaluationQualityGate;
import com.agentmind.evaluation.model.RagEvaluationQualityGateResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 评估任务 PostgreSQL 适配器，使用状态条件更新保证异步任务并发安全。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.evaluation", name = "store", havingValue = "jdbc")
public class JdbcRagEvaluationJobRepository implements RagEvaluationJobRepository {

    private static final String COLUMNS = "id, owner_user_id, workspace_id, dataset_id, dataset_version, status, "
            + "retrieval_strategy, top_k, prompt_version, model_name, experiment_config::text, baseline_job_id, "
            + "retry_of_job_id, total_cases, completed_cases, progress, metrics::text, quality_gate::text, "
            + "quality_gate_result::text, case_results::text, failure_reason, created_at, started_at, updated_at, "
            + "completed_at, attempt_count, recovery_count, lease_owner, lease_expires_at, heartbeat_at";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcRagEvaluationJobRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public RagEvaluationJob save(RagEvaluationJob job) {
        if (job.id() != null) {
            return update(job, "where id = ? and owner_user_id = ? and workspace_id = ?", List.of(
                    job.id(), job.ownerUserId(), job.workspaceId()
            )).orElseThrow(() -> new IllegalStateException("更新评估任务失败"));
        }
        return jdbcTemplate.query("""
                        insert into rag_evaluation_jobs (
                            owner_user_id, workspace_id, dataset_id, dataset_version, status,
                            retrieval_strategy, top_k, prompt_version, model_name, experiment_config,
                            baseline_job_id, retry_of_job_id, total_cases, completed_cases, progress,
                            metrics, quality_gate, quality_gate_result, case_results, failure_reason,
                            created_at, started_at, updated_at, completed_at, attempt_count, recovery_count,
                            lease_owner, lease_expires_at, heartbeat_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?,
                                  cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), ?, ?, ?, ?, ?,
                                  ?, ?, ?, ?, ?)
                        returning %s
                        """.formatted(COLUMNS), this::mapJob,
                job.ownerUserId(), job.workspaceId(), job.datasetId(), job.datasetVersion(), job.status().name(),
                job.retrievalStrategy(), job.topK(), job.promptVersion(), job.modelName(),
                writeJson(job.experimentConfig()), job.baselineJobId(), job.retryOfJobId(), job.totalCases(),
                job.completedCases(), job.progress(), writeJson(job.metrics()), writeJson(job.qualityGate()),
                writeJson(job.qualityGateResult()), writeJson(safeResults(job.caseResults())), safe(job.failureReason()),
                job.createdAt(), job.startedAt(), job.updatedAt(), job.completedAt(), job.attemptCount(),
                job.recoveryCount(), safe(job.leaseOwner()), job.leaseExpiresAt(), job.heartbeatAt()).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("创建评估任务失败"));
    }

    @Override
    public Optional<RagEvaluationJob> updateIfStatus(
            RagEvaluationJob job,
            Set<RagEvaluationJobStatus> expectedStatuses
    ) {
        if (expectedStatuses.isEmpty()) {
            return Optional.empty();
        }
        String placeholders = expectedStatuses.stream().map(ignored -> "?").collect(Collectors.joining(", "));
        List<Object> whereParameters = new ArrayList<>(List.of(job.id(), job.ownerUserId(), job.workspaceId()));
        expectedStatuses.stream().map(Enum::name).forEach(whereParameters::add);
        return update(job,
                "where id = ? and owner_user_id = ? and workspace_id = ? and status in (" + placeholders + ")",
                whereParameters);
    }

    @Override
    public Optional<RagEvaluationJob> claim(
            Long ownerUserId,
            Long workspaceId,
            Long jobId,
            String leaseOwner,
            OffsetDateTime now,
            OffsetDateTime leaseExpiresAt
    ) {
        return jdbcTemplate.query("""
                        update rag_evaluation_jobs
                        set status = 'RUNNING', lease_owner = ?, lease_expires_at = ?, heartbeat_at = ?,
                            attempt_count = attempt_count + 1, started_at = coalesce(started_at, ?), updated_at = ?
                        where id = ? and owner_user_id = ? and workspace_id = ? and status = 'PENDING'
                        returning %s
                        """.formatted(COLUMNS), this::mapJob, leaseOwner, leaseExpiresAt, now, now, now,
                jobId, ownerUserId, workspaceId).stream().findFirst();
    }

    @Override
    public boolean renewLease(Long jobId, String leaseOwner, OffsetDateTime now, OffsetDateTime leaseExpiresAt) {
        return jdbcTemplate.update("""
                        update rag_evaluation_jobs
                        set heartbeat_at = ?, lease_expires_at = ?, updated_at = ?
                        where id = ? and lease_owner = ? and status in ('RUNNING', 'CANCEL_REQUESTED')
                          and lease_expires_at > ?
                        """, now, leaseExpiresAt, now, jobId, leaseOwner, now) == 1;
    }

    @Override
    public Optional<RagEvaluationJob> updateIfStatusAndLeaseOwner(
            RagEvaluationJob job,
            Set<RagEvaluationJobStatus> expectedStatuses,
            String leaseOwner,
            OffsetDateTime now
    ) {
        if (expectedStatuses.isEmpty()) {
            return Optional.empty();
        }
        String placeholders = expectedStatuses.stream().map(ignored -> "?").collect(Collectors.joining(", "));
        List<Object> parameters = new ArrayList<>(List.of(
                job.id(), job.ownerUserId(), job.workspaceId(), leaseOwner, now
        ));
        expectedStatuses.stream().map(Enum::name).forEach(parameters::add);
        return update(job,
                "where id = ? and owner_user_id = ? and workspace_id = ? and lease_owner = ? "
                        + "and lease_expires_at > ? and status in (" + placeholders + ")",
                parameters);
    }

    @Override
    public List<RagEvaluationJob> recoverExpiredLeases(OffsetDateTime now, int limit) {
        return jdbcTemplate.query("""
                with expired as (
                    select id
                    from rag_evaluation_jobs
                    where status in ('RUNNING', 'CANCEL_REQUESTED')
                      and lease_expires_at is not null
                      and lease_expires_at <= ?
                    order by lease_expires_at, id
                    for update skip locked
                    limit ?
                )
                update rag_evaluation_jobs job
                set status = case when job.status = 'CANCEL_REQUESTED' then 'CANCELED' else 'PENDING' end,
                    recovery_count = recovery_count + 1,
                    lease_owner = '', lease_expires_at = null, updated_at = ?,
                    completed_at = case when job.status = 'CANCEL_REQUESTED' then ? else completed_at end,
                    failure_reason = case when job.status = 'CANCEL_REQUESTED'
                        then '取消请求期间执行实例失联，任务已安全取消' else failure_reason end
                from expired
                where job.id = expired.id
                returning job.*
                """, this::mapJob, now, limit, now, now);
    }

    @Override
    public List<RagEvaluationJob> findPendingJobs(int limit) {
        return jdbcTemplate.query(
                "select " + COLUMNS + " from rag_evaluation_jobs "
                        + "where status = 'PENDING' order by created_at, id limit ?",
                this::mapJob,
                limit
        );
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
    public List<RagEvaluationJob> findSuccessfulByDataset(
            Long ownerUserId,
            Long workspaceId,
            Long datasetId,
            Integer datasetVersion,
            int limit
    ) {
        if (datasetVersion == null) {
            return jdbcTemplate.query(
                    "select " + COLUMNS + " from rag_evaluation_jobs "
                            + "where owner_user_id = ? and workspace_id = ? and dataset_id = ? "
                            + "and status = 'SUCCEEDED' order by completed_at desc, id desc limit ?",
                    this::mapJob, ownerUserId, workspaceId, datasetId, limit
            );
        }
        return jdbcTemplate.query(
                "select " + COLUMNS + " from rag_evaluation_jobs "
                        + "where owner_user_id = ? and workspace_id = ? and dataset_id = ? "
                        + "and dataset_version = ? and status = 'SUCCEEDED' "
                        + "order by completed_at desc, id desc limit ?",
                this::mapJob, ownerUserId, workspaceId, datasetId, datasetVersion, limit
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

    private Optional<RagEvaluationJob> update(RagEvaluationJob job, String whereClause, List<Object> whereParameters) {
        List<Object> parameters = new ArrayList<>();
        parameters.add(job.status().name());
        parameters.add(job.baselineJobId());
        parameters.add(job.retryOfJobId());
        parameters.add(job.completedCases());
        parameters.add(job.progress());
        parameters.add(writeJson(job.metrics()));
        parameters.add(writeJson(job.qualityGateResult()));
        parameters.add(writeJson(safeResults(job.caseResults())));
        parameters.add(safe(job.failureReason()));
        parameters.add(job.startedAt());
        parameters.add(job.updatedAt());
        parameters.add(job.completedAt());
        parameters.add(job.attemptCount());
        parameters.add(job.recoveryCount());
        parameters.add(job.terminal());
        parameters.add(job.terminal());
        parameters.addAll(whereParameters);
        return jdbcTemplate.query("""
                        update rag_evaluation_jobs
                        set status = ?, baseline_job_id = ?, retry_of_job_id = ?, completed_cases = ?, progress = ?,
                            metrics = cast(? as jsonb), quality_gate_result = cast(? as jsonb),
                            case_results = cast(? as jsonb), failure_reason = ?, started_at = ?, updated_at = ?,
                            completed_at = ?, attempt_count = ?, recovery_count = ?,
                            lease_owner = case when ? then '' else lease_owner end,
                            lease_expires_at = case when ? then null else lease_expires_at end
                        %s
                        returning %s
                        """.formatted(whereClause, COLUMNS), this::mapJob, parameters.toArray()).stream().findFirst();
    }

    private RagEvaluationJob mapJob(ResultSet resultSet, int rowNumber) throws SQLException {
        RagEvaluationExperimentConfig experimentConfig = readJson(
                resultSet.getString("experiment_config"), RagEvaluationExperimentConfig.class
        );
        if (experimentConfig == null) {
            // 兼容尚未执行 Stage 9 增量迁移的历史任务快照。
            experimentConfig = new RagEvaluationExperimentConfig(
                    "历史任务-" + resultSet.getLong("id"), "legacy",
                    com.agentmind.evaluation.model.RagEvaluationRetrievalStrategy.VECTOR,
                    resultSet.getInt("top_k"), com.agentmind.evaluation.model.RagEvaluationRerankStrategy.NONE,
                    resultSet.getInt("top_k"), resultSet.getString("prompt_version"), resultSet.getString("model_name")
            );
        }
        return new RagEvaluationJob(
                resultSet.getLong("id"), resultSet.getLong("owner_user_id"), resultSet.getLong("workspace_id"),
                resultSet.getLong("dataset_id"), resultSet.getInt("dataset_version"),
                RagEvaluationJobStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("retrieval_strategy"), resultSet.getInt("top_k"),
                resultSet.getString("prompt_version"), resultSet.getString("model_name"),
                experimentConfig,
                nullableLong(resultSet, "baseline_job_id"), nullableLong(resultSet, "retry_of_job_id"),
                resultSet.getInt("total_cases"), resultSet.getInt("completed_cases"), resultSet.getInt("progress"),
                readJson(resultSet.getString("metrics"), RagEvaluationMetrics.class),
                readJson(resultSet.getString("quality_gate"), RagEvaluationQualityGate.class),
                readJson(resultSet.getString("quality_gate_result"), RagEvaluationQualityGateResult.class),
                readResults(resultSet.getString("case_results")), resultSet.getString("failure_reason"),
                resultSet.getInt("attempt_count"), resultSet.getInt("recovery_count"),
                resultSet.getString("lease_owner"),
                resultSet.getObject("lease_expires_at", OffsetDateTime.class),
                resultSet.getObject("heartbeat_at", OffsetDateTime.class),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("started_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class),
                resultSet.getObject("completed_at", OffsetDateTime.class)
        );
    }

    private <T> T readJson(String json, Class<T> type) throws SQLException {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new SQLException("读取评估任务快照失败", exception);
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

    private List<RagEvaluationCaseResult> safeResults(List<RagEvaluationCaseResult> results) {
        return results == null ? List.of() : results;
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
