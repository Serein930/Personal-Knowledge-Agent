package com.agentmind.agent.confirmation.repository;

import com.agentmind.agent.confirmation.model.AgentToolConfirmation;
import com.agentmind.agent.confirmation.model.AgentToolConfirmationStatus;
import com.agentmind.agent.model.dto.AgentToolExecutionResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 写工具确认单 PostgreSQL 适配器。
 *
 * <p>状态迁移 SQL 同时校验用户、知识空间、确认单编号和原状态。即使多个应用实例同时收到确认请求，
 * 也只有一个请求能够取得执行权。</p>
 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.agent.persistence", name = "store", havingValue = "jdbc")
public class JdbcAgentToolConfirmationRepository implements AgentToolConfirmationRepository {

    private static final String COLUMNS = """
            id, owner_user_id, workspace_id, conversation_id, message_id, request_id, tool_name,
            arguments_json, argument_summary, token_digest, status, execution_response_json,
            failure_reason, created_at, expires_at, updated_at, executed_at
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<AgentToolConfirmation> rowMapper = this::mapConfirmation;

    public JdbcAgentToolConfirmationRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentToolConfirmation save(AgentToolConfirmation confirmation) {
        if (confirmation.id() == null) {
            Long id = jdbcTemplate.queryForObject("""
                    insert into agent_tool_confirmations (
                        owner_user_id, workspace_id, conversation_id, message_id, request_id, tool_name,
                        arguments_json, argument_summary, token_digest, status, execution_response_json,
                        failure_reason, created_at, expires_at, updated_at, executed_at
                    ) values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?)
                    returning id
                    """, Long.class, valuesWithoutId(confirmation));
            return confirmation.withId(id);
        }
        jdbcTemplate.update("""
                update agent_tool_confirmations
                set status = ?, execution_response_json = cast(? as jsonb), failure_reason = ?,
                    updated_at = ?, executed_at = ?
                where id = ? and owner_user_id = ? and workspace_id = ?
                """,
                confirmation.status().name(), serializeExecution(confirmation.executionResponse()),
                confirmation.failureReason(), confirmation.updatedAt(), confirmation.executedAt(),
                confirmation.id(), confirmation.ownerUserId(), confirmation.workspaceId());
        return confirmation;
    }

    @Override
    public Optional<AgentToolConfirmation> findByOwnerUserIdAndWorkspaceIdAndId(
            Long ownerUserId,
            Long workspaceId,
            Long confirmationId
    ) {
        return jdbcTemplate.query(
                        "select " + COLUMNS + " from agent_tool_confirmations "
                                + "where owner_user_id = ? and workspace_id = ? and id = ?",
                        rowMapper,
                        ownerUserId,
                        workspaceId,
                        confirmationId
                ).stream()
                .findFirst();
    }

    @Override
    public Optional<AgentToolConfirmation> compareAndSetStatus(
            Long ownerUserId,
            Long workspaceId,
            Long confirmationId,
            AgentToolConfirmationStatus expectedStatus,
            AgentToolConfirmationStatus targetStatus,
            OffsetDateTime updatedAt,
            String failureReason
    ) {
        return jdbcTemplate.query("""
                        update agent_tool_confirmations
                        set status = ?, updated_at = ?, failure_reason = ?
                        where owner_user_id = ? and workspace_id = ? and id = ? and status = ?
                        returning %s
                        """.formatted(COLUMNS), rowMapper,
                        targetStatus.name(), updatedAt, failureReason, ownerUserId, workspaceId, confirmationId,
                        expectedStatus.name())
                .stream()
                .findFirst();
    }

    @Override
    public List<AgentToolConfirmation> findExpiredPendingConfirmations(OffsetDateTime now, int limit) {
        return jdbcTemplate.query(
                "select " + COLUMNS + " from agent_tool_confirmations "
                        + "where status = 'PENDING_CONFIRMATION' and expires_at <= ? "
                        + "order by expires_at, id limit ?",
                rowMapper,
                now,
                Math.max(0, limit)
        );
    }

    @Override
    public List<AgentToolConfirmation> findStaleExecutingConfirmations(
            OffsetDateTime updatedBefore,
            int limit
    ) {
        return jdbcTemplate.query(
                "select " + COLUMNS + " from agent_tool_confirmations "
                        + "where status = 'EXECUTING' and updated_at <= ? "
                        + "order by updated_at, id limit ?",
                rowMapper,
                updatedBefore,
                Math.max(0, limit)
        );
    }

    private Object[] valuesWithoutId(AgentToolConfirmation confirmation) {
        return new Object[]{
                confirmation.ownerUserId(), confirmation.workspaceId(), confirmation.conversationId(),
                confirmation.messageId(), confirmation.requestId(), confirmation.toolName(),
                confirmation.arguments().toString(), confirmation.argumentSummary(), confirmation.tokenDigest(),
                confirmation.status().name(), serializeExecution(confirmation.executionResponse()),
                confirmation.failureReason(), confirmation.createdAt(), confirmation.expiresAt(),
                confirmation.updatedAt(), confirmation.executedAt()
        };
    }

    private AgentToolConfirmation mapConfirmation(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AgentToolConfirmation(
                resultSet.getLong("id"),
                resultSet.getLong("owner_user_id"),
                resultSet.getLong("workspace_id"),
                nullableLong(resultSet, "conversation_id"),
                nullableLong(resultSet, "message_id"),
                resultSet.getString("request_id"),
                resultSet.getString("tool_name"),
                readJson(resultSet.getString("arguments_json")),
                resultSet.getString("argument_summary"),
                resultSet.getString("token_digest"),
                AgentToolConfirmationStatus.valueOf(resultSet.getString("status")),
                readExecution(resultSet.getString("execution_response_json")),
                resultSet.getString("failure_reason"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("expires_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class),
                resultSet.getObject("executed_at", OffsetDateTime.class)
        );
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private String serializeExecution(AgentToolExecutionResponse executionResponse) {
        if (executionResponse == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(executionResponse);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化写工具执行响应失败", exception);
        }
    }

    private AgentToolExecutionResponse readExecution(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AgentToolExecutionResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("反序列化写工具执行响应失败", exception);
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("反序列化写工具参数失败", exception);
        }
    }
}
