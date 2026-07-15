package com.agentmind.evaluation.repository;

import com.agentmind.evaluation.model.RagEvaluationCase;
import com.agentmind.evaluation.model.RagEvaluationDataset;
import com.agentmind.evaluation.model.RagEvaluationDatasetVersion;
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
import org.springframework.transaction.annotation.Transactional;

/** 固定评估集 PostgreSQL 适配器。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.evaluation", name = "store", havingValue = "jdbc")
public class JdbcRagEvaluationDatasetRepository implements RagEvaluationDatasetRepository {

    private static final String DATASET_COLUMNS = "id, owner_user_id, workspace_id, name, description, "
            + "latest_version, created_at, updated_at";
    private static final String VERSION_COLUMNS = "id, dataset_id, owner_user_id, workspace_id, version, "
            + "change_note, cases::text, created_at";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcRagEvaluationDatasetRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public RagEvaluationDataset saveDataset(RagEvaluationDataset dataset) {
        return jdbcTemplate.query("""
                        insert into rag_evaluation_datasets (
                            owner_user_id, workspace_id, name, description,
                            latest_version, created_at, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?)
                        returning %s
                        """.formatted(DATASET_COLUMNS), this::mapDataset,
                dataset.ownerUserId(), dataset.workspaceId(), dataset.name(), dataset.description(),
                dataset.latestVersion(), dataset.createdAt(), dataset.updatedAt()).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("保存固定评估集失败"));
    }

    @Override
    @Transactional
    public RagEvaluationDatasetVersion saveVersion(RagEvaluationDatasetVersion version) {
        RagEvaluationDatasetVersion saved = jdbcTemplate.query("""
                        insert into rag_evaluation_dataset_versions (
                            dataset_id, owner_user_id, workspace_id, version, change_note, cases, created_at
                        ) values (?, ?, ?, ?, ?, cast(? as jsonb), ?)
                        returning %s
                        """.formatted(VERSION_COLUMNS), this::mapVersion,
                version.datasetId(), version.ownerUserId(), version.workspaceId(), version.version(),
                version.changeNote(), writeCases(version.cases()), version.createdAt()).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("保存固定评估集版本失败"));
        int updated = jdbcTemplate.update("""
                        update rag_evaluation_datasets
                        set latest_version = ?, updated_at = ?
                        where id = ? and owner_user_id = ? and workspace_id = ? and latest_version < ?
                        """, saved.version(), saved.createdAt(), saved.datasetId(), saved.ownerUserId(),
                saved.workspaceId(), saved.version());
        if (updated != 1) {
            throw new IllegalStateException("评估集版本更新发生并发冲突");
        }
        return saved;
    }

    @Override
    public Optional<RagEvaluationDataset> findDatasetByScopeAndId(
            Long ownerUserId,
            Long workspaceId,
            Long datasetId
    ) {
        return jdbcTemplate.query(
                "select " + DATASET_COLUMNS + " from rag_evaluation_datasets "
                        + "where owner_user_id = ? and workspace_id = ? and id = ?",
                this::mapDataset, ownerUserId, workspaceId, datasetId
        ).stream().findFirst();
    }

    @Override
    public Optional<RagEvaluationDatasetVersion> findVersionByScopeAndDatasetIdAndVersion(
            Long ownerUserId,
            Long workspaceId,
            Long datasetId,
            int version
    ) {
        return jdbcTemplate.query(
                "select " + VERSION_COLUMNS + " from rag_evaluation_dataset_versions "
                        + "where owner_user_id = ? and workspace_id = ? and dataset_id = ? and version = ?",
                this::mapVersion, ownerUserId, workspaceId, datasetId, version
        ).stream().findFirst();
    }

    @Override
    public List<RagEvaluationDataset> findDatasetsByScope(
            Long ownerUserId,
            Long workspaceId,
            int offset,
            int limit
    ) {
        return jdbcTemplate.query(
                "select " + DATASET_COLUMNS + " from rag_evaluation_datasets "
                        + "where owner_user_id = ? and workspace_id = ? "
                        + "order by updated_at desc, id desc offset ? limit ?",
                this::mapDataset, ownerUserId, workspaceId, offset, limit
        );
    }

    @Override
    public List<RagEvaluationDatasetVersion> findVersionsByScopeAndDatasetId(
            Long ownerUserId,
            Long workspaceId,
            Long datasetId
    ) {
        return jdbcTemplate.query(
                "select " + VERSION_COLUMNS + " from rag_evaluation_dataset_versions "
                        + "where owner_user_id = ? and workspace_id = ? and dataset_id = ? order by version desc",
                this::mapVersion, ownerUserId, workspaceId, datasetId
        );
    }

    @Override
    public boolean existsByScopeAndName(Long ownerUserId, Long workspaceId, String name) {
        Boolean exists = jdbcTemplate.queryForObject("""
                select exists(
                    select 1 from rag_evaluation_datasets
                    where owner_user_id = ? and workspace_id = ? and lower(name) = lower(?)
                )
                """, Boolean.class, ownerUserId, workspaceId, name);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public long countDatasetsByScope(Long ownerUserId, Long workspaceId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from rag_evaluation_datasets where owner_user_id = ? and workspace_id = ?",
                Long.class, ownerUserId, workspaceId
        );
        return count == null ? 0 : count;
    }

    private RagEvaluationDataset mapDataset(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RagEvaluationDataset(
                resultSet.getLong("id"), resultSet.getLong("owner_user_id"),
                resultSet.getLong("workspace_id"), resultSet.getString("name"),
                resultSet.getString("description"), resultSet.getInt("latest_version"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private RagEvaluationDatasetVersion mapVersion(ResultSet resultSet, int rowNumber) throws SQLException {
        try {
            List<RagEvaluationCase> cases = objectMapper.readValue(
                    resultSet.getString("cases"), new TypeReference<>() {
                    }
            );
            return new RagEvaluationDatasetVersion(
                    resultSet.getLong("id"), resultSet.getLong("dataset_id"),
                    resultSet.getLong("owner_user_id"), resultSet.getLong("workspace_id"),
                    resultSet.getInt("version"), resultSet.getString("change_note"), cases,
                    resultSet.getObject("created_at", OffsetDateTime.class)
            );
        } catch (JsonProcessingException exception) {
            throw new SQLException("读取固定评估集题目失败", exception);
        }
    }

    private String writeCases(List<RagEvaluationCase> cases) {
        try {
            return objectMapper.writeValueAsString(cases);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化固定评估集题目失败", exception);
        }
    }
}
