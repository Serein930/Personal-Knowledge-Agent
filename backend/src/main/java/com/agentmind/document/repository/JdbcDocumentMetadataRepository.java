package com.agentmind.document.repository;

import com.agentmind.document.model.DocumentMetadata;
import com.agentmind.document.model.DocumentSourceType;
import com.agentmind.document.model.IngestionStatus;
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
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** PostgreSQL 文档元数据仓储，标签使用 JSONB 保持跨数据库访问层的稳定契约。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.core.persistence", name = "store", havingValue = "jdbc")
public class JdbcDocumentMetadataRepository implements DocumentMetadataRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcDocumentMetadataRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public DocumentMetadata create(Long ownerUserId, Long workspaceId, String title,
            DocumentSourceType sourceType, String sourceUri, String originalFilename, List<String> tags) {
        OffsetDateTime now = OffsetDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    insert into knowledge_document (
                        owner_user_id, workspace_id, title, source_type, source_uri, original_filename,
                        tags, ingestion_status, created_at, updated_at
                    ) values (?, ?, ?, ?, ?, ?, cast(? as jsonb), 'RUNNING', ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, ownerUserId);
            statement.setLong(2, workspaceId);
            statement.setString(3, title);
            statement.setString(4, sourceType.name());
            statement.setString(5, sourceUri);
            statement.setString(6, originalFilename);
            statement.setString(7, writeTags(tags));
            statement.setObject(8, now);
            statement.setObject(9, now);
            return statement;
        }, keyHolder);
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("创建文档元数据后未返回主键");
        }
        return findByWorkspaceIdAndId(workspaceId, id.longValue()).orElseThrow();
    }

    @Override
    public void markSucceeded(Long documentId, String storageKey, String contentType, long contentSize,
            String contentHash, int chunkCount) {
        jdbcTemplate.update("""
                update knowledge_document
                set storage_key = ?, content_type = ?, content_size = ?, content_hash = ?, chunk_count = ?,
                    ingestion_status = 'SUCCEEDED', updated_at = ?
                where id = ? and deleted_at is null
                """, storageKey, contentType, contentSize, contentHash, chunkCount, OffsetDateTime.now(), documentId);
    }

    @Override
    public void markFailed(Long documentId) {
        jdbcTemplate.update("""
                update knowledge_document
                set ingestion_status = 'FAILED', chunk_count = 0, updated_at = ?
                where id = ? and deleted_at is null
                """, OffsetDateTime.now(), documentId);
    }

    @Override
    public Optional<DocumentMetadata> rename(Long workspaceId, Long documentId, String title) {
        return jdbcTemplate.query("""
                update knowledge_document set title = ?, updated_at = ?
                where workspace_id = ? and id = ? and deleted_at is null
                returning *
                """, this::mapDocument, title, OffsetDateTime.now(), workspaceId, documentId)
                .stream().findFirst();
    }

    @Override
    public boolean softDelete(Long workspaceId, Long documentId) {
        OffsetDateTime now = OffsetDateTime.now();
        return jdbcTemplate.update("""
                update knowledge_document set deleted_at = ?, updated_at = ?
                where workspace_id = ? and id = ? and deleted_at is null
                """, now, now, workspaceId, documentId) == 1;
    }

    @Override
    public Optional<DocumentMetadata> findByWorkspaceIdAndId(Long workspaceId, Long documentId) {
        return jdbcTemplate.query("""
                select * from knowledge_document
                where workspace_id = ? and id = ? and deleted_at is null
                """, this::mapDocument, workspaceId, documentId).stream().findFirst();
    }

    @Override
    public Optional<DocumentMetadata> findById(Long documentId) {
        return jdbcTemplate.query("""
                select * from knowledge_document where id = ? and deleted_at is null
                """, this::mapDocument, documentId).stream().findFirst();
    }

    @Override
    public Optional<DocumentMetadata> findLatestByWorkspaceIdAndSourceUri(Long workspaceId, String sourceUri) {
        return jdbcTemplate.query("""
                select * from knowledge_document
                where workspace_id = ? and source_uri = ? and ingestion_status = 'SUCCEEDED'
                  and deleted_at is null
                order by created_at desc limit 1
                """, this::mapDocument, workspaceId, sourceUri).stream().findFirst();
    }

    @Override
    public List<DocumentMetadata> findAllByWorkspaceId(Long workspaceId) {
        return jdbcTemplate.query("""
                select * from knowledge_document
                where workspace_id = ? and deleted_at is null
                order by updated_at desc
                """, this::mapDocument, workspaceId);
    }

    private DocumentMetadata mapDocument(ResultSet resultSet, int rowNumber) throws SQLException {
        return new DocumentMetadata(
                resultSet.getLong("id"), resultSet.getLong("owner_user_id"), resultSet.getLong("workspace_id"),
                resultSet.getString("title"), DocumentSourceType.valueOf(resultSet.getString("source_type")),
                resultSet.getString("source_uri"), resultSet.getString("original_filename"),
                resultSet.getString("storage_key"), resultSet.getString("content_type"),
                resultSet.getLong("content_size"), resultSet.getString("content_hash"),
                readTags(resultSet.getString("tags")),
                IngestionStatus.valueOf(resultSet.getString("ingestion_status")),
                resultSet.getInt("chunk_count"), resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class));
    }

    private String writeTags(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags == null ? List.of() : tags);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化文档标签失败", exception);
        }
    }

    private List<String> readTags(String json) throws SQLException {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new SQLException("反序列化文档标签失败", exception);
        }
    }
}
