package com.agentmind.knowledge.outbox.repository;

import com.agentmind.knowledge.outbox.model.KnowledgeIndexOutboxEvent;
import com.agentmind.knowledge.outbox.model.KnowledgeIndexOutboxOperation;
import com.agentmind.knowledge.outbox.model.KnowledgeIndexOutboxPayload;
import com.agentmind.knowledge.outbox.model.KnowledgeIndexOutboxStatistics;
import com.agentmind.knowledge.outbox.model.KnowledgeIndexOutboxStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL Outbox 适配器，所有状态迁移都校验当前租约持有者。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.knowledge-index.outbox", name = "enabled", havingValue = "true")
public class JdbcKnowledgeIndexOutboxRepository implements KnowledgeIndexOutboxRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcKnowledgeIndexOutboxRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void enqueue(
            String eventKey,
            Long workspaceId,
            Long documentId,
            KnowledgeIndexOutboxOperation operation,
            KnowledgeIndexOutboxPayload payload,
            OffsetDateTime now
    ) {
        jdbcTemplate.update("""
                insert into knowledge_index_outbox (
                    event_key, workspace_id, document_id, operation, payload, status,
                    attempts, available_at, lease_owner, last_error, created_at, updated_at
                ) values (?, ?, ?, ?, cast(? as jsonb), 'PENDING', 0, ?, '', '', ?, ?)
                on conflict (event_key) do nothing
                """, eventKey, workspaceId, documentId, operation.name(), writePayload(payload), now, now, now);
    }

    @Override
    public List<KnowledgeIndexOutboxEvent> claimBatch(
            Long workspaceId,
            String leaseOwner,
            OffsetDateTime now,
            OffsetDateTime leaseExpiresAt,
            int limit
    ) {
        return jdbcTemplate.query("""
                with candidates as (
                    select id
                    from knowledge_index_outbox
                    where (cast(? as bigint) is null or workspace_id = ?)
                      and ((status in ('PENDING', 'RETRY') and available_at <= ?)
                           or (status = 'PROCESSING' and lease_expires_at <= ?))
                    order by available_at, id
                    for update skip locked
                    limit ?
                )
                update knowledge_index_outbox event
                set status = 'PROCESSING', attempts = attempts + 1,
                    lease_owner = ?, lease_expires_at = ?, updated_at = ?
                from candidates
                where event.id = candidates.id
                returning event.*
                """, this::mapEvent, workspaceId, workspaceId, now, now, limit, leaseOwner, leaseExpiresAt, now);
    }

    @Override
    public boolean markCompleted(Long eventId, String leaseOwner, OffsetDateTime now) {
        return jdbcTemplate.update("""
                update knowledge_index_outbox
                set status = 'COMPLETED', lease_owner = '', lease_expires_at = null,
                    last_error = '', updated_at = ?, completed_at = ?
                where id = ? and status = 'PROCESSING' and lease_owner = ? and lease_expires_at > ?
                """, now, now, eventId, leaseOwner, now) == 1;
    }

    @Override
    public boolean markFailed(
            Long eventId,
            String leaseOwner,
            String reason,
            OffsetDateTime nextAvailableAt,
            boolean dead,
            OffsetDateTime now
    ) {
        return jdbcTemplate.update("""
                update knowledge_index_outbox
                set status = ?, available_at = ?, lease_owner = '', lease_expires_at = null,
                    last_error = ?, updated_at = ?, completed_at = case when ? then ? else null end
                where id = ? and status = 'PROCESSING' and lease_owner = ? and lease_expires_at > ?
                """, dead ? "DEAD" : "RETRY", nextAvailableAt, limit(reason, 2000), now,
                dead, now, eventId, leaseOwner, now) == 1;
    }

    @Override
    public KnowledgeIndexOutboxStatistics statistics(Long workspaceId) {
        java.util.Map<String, Long> counts = new java.util.HashMap<>();
        List<java.util.Map<String, Object>> rows = workspaceId == null
                ? jdbcTemplate.queryForList(
                        "select status, count(*) as total from knowledge_index_outbox group by status")
                : jdbcTemplate.queryForList(
                        "select status, count(*) as total from knowledge_index_outbox where workspace_id = ? group by status",
                        workspaceId);
        rows
                .forEach(row -> counts.put((String) row.get("status"), ((Number) row.get("total")).longValue()));
        return new KnowledgeIndexOutboxStatistics(
                counts.getOrDefault("PENDING", 0L),
                counts.getOrDefault("PROCESSING", 0L),
                counts.getOrDefault("RETRY", 0L),
                counts.getOrDefault("COMPLETED", 0L),
                counts.getOrDefault("DEAD", 0L)
        );
    }

    private KnowledgeIndexOutboxEvent mapEvent(ResultSet resultSet, int rowNumber) throws SQLException {
        return new KnowledgeIndexOutboxEvent(
                resultSet.getLong("id"),
                resultSet.getString("event_key"),
                resultSet.getLong("workspace_id"),
                resultSet.getLong("document_id"),
                KnowledgeIndexOutboxOperation.valueOf(resultSet.getString("operation")),
                readPayload(resultSet.getString("payload")),
                KnowledgeIndexOutboxStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("attempts"),
                resultSet.getObject("available_at", OffsetDateTime.class),
                resultSet.getString("lease_owner"),
                resultSet.getObject("lease_expires_at", OffsetDateTime.class),
                resultSet.getString("last_error"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class),
                resultSet.getObject("completed_at", OffsetDateTime.class)
        );
    }

    private String writePayload(KnowledgeIndexOutboxPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化知识索引事务消息失败", exception);
        }
    }

    private KnowledgeIndexOutboxPayload readPayload(String json) throws SQLException {
        try {
            return objectMapper.readValue(json, KnowledgeIndexOutboxPayload.class);
        } catch (JsonProcessingException exception) {
            throw new SQLException("读取知识索引事务消息失败", exception);
        }
    }

    private String limit(String value, int maximumLength) {
        String safe = value == null || value.isBlank() ? "未知索引异常" : value;
        return safe.length() <= maximumLength ? safe : safe.substring(0, maximumLength);
    }
}
