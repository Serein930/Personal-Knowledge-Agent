package com.agentmind.agent.audit.repository;

import com.agentmind.agent.audit.model.AgentToolCallAudit;
import com.agentmind.agent.audit.model.AgentToolCallStatus;
import com.agentmind.agent.audit.model.AgentToolType;
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
 * 智能体工具调用审计 PostgreSQL 适配器。
 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.agent.persistence", name = "store", havingValue = "jdbc")
public class JdbcAgentToolCallAuditRepository implements AgentToolCallAuditRepository {

    private static final String COLUMNS = """
            id, owner_user_id, workspace_id, conversation_id, message_id, request_id, tool_name,
            tool_type, request_summary, request_fingerprint, response_summary, status,
            error_message, latency_ms, created_at
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<AgentToolCallAudit> rowMapper = this::mapAudit;

    public JdbcAgentToolCallAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AgentToolCallAudit save(AgentToolCallAudit audit) {
        if (audit.getId() == null) {
            Long id = jdbcTemplate.queryForObject("""
                    insert into agent_tool_call_audits (
                        owner_user_id, workspace_id, conversation_id, message_id, request_id, tool_name,
                        tool_type, request_summary, request_fingerprint, response_summary, status,
                        error_message, latency_ms, created_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    returning id
                    """, Long.class,
                    audit.getOwnerUserId(), audit.getWorkspaceId(), audit.getConversationId(), audit.getMessageId(),
                    audit.getRequestId(), audit.getToolName(), enumName(audit.getToolType()), audit.getRequestPayload(),
                    audit.getRequestFingerprint(), audit.getResponseSummary(), audit.getStatus().name(),
                    audit.getErrorMessage(), audit.getLatencyMs(), audit.getCreatedAt());
            audit.setId(id);
            return audit;
        }
        jdbcTemplate.update("""
                update agent_tool_call_audits
                set tool_type = ?, response_summary = ?, status = ?, error_message = ?, latency_ms = ?
                where id = ? and owner_user_id = ? and workspace_id = ?
                """,
                enumName(audit.getToolType()), audit.getResponseSummary(), audit.getStatus().name(),
                audit.getErrorMessage(), audit.getLatencyMs(), audit.getId(), audit.getOwnerUserId(),
                audit.getWorkspaceId());
        return audit;
    }

    @Override
    public Optional<AgentToolCallAudit> findSucceededByExecutionKey(
            Long ownerUserId,
            Long workspaceId,
            String toolName,
            String requestId
    ) {
        return jdbcTemplate.query(
                        "select " + COLUMNS + " from agent_tool_call_audits "
                                + "where owner_user_id = ? and workspace_id = ? and tool_name = ? "
                                + "and request_id = ? and status = 'SUCCEEDED' limit 1",
                        rowMapper, ownerUserId, workspaceId, toolName, requestId
                ).stream()
                .findFirst();
    }

    @Override
    public List<AgentToolCallAudit> findByOwnerUserIdAndWorkspaceId(Long ownerUserId, Long workspaceId) {
        return jdbcTemplate.query(
                "select " + COLUMNS + " from agent_tool_call_audits "
                        + "where owner_user_id = ? and workspace_id = ? order by created_at desc, id desc",
                rowMapper, ownerUserId, workspaceId
        );
    }

    @Override
    public List<AgentToolCallAudit> findByExecutionContext(
            Long ownerUserId,
            Long workspaceId,
            Long conversationId,
            Long messageId
    ) {
        return jdbcTemplate.query(
                "select " + COLUMNS + " from agent_tool_call_audits "
                        + "where owner_user_id = ? and workspace_id = ? and conversation_id = ? and message_id = ? "
                        + "order by created_at, id",
                rowMapper, ownerUserId, workspaceId, conversationId, messageId
        );
    }

    private AgentToolCallAudit mapAudit(ResultSet resultSet, int rowNumber) throws SQLException {
        AgentToolCallAudit audit = new AgentToolCallAudit();
        audit.setId(resultSet.getLong("id"));
        audit.setOwnerUserId(resultSet.getLong("owner_user_id"));
        audit.setWorkspaceId(resultSet.getLong("workspace_id"));
        audit.setConversationId(nullableLong(resultSet, "conversation_id"));
        audit.setMessageId(nullableLong(resultSet, "message_id"));
        audit.setRequestId(resultSet.getString("request_id"));
        audit.setToolName(resultSet.getString("tool_name"));
        String toolType = resultSet.getString("tool_type");
        audit.setToolType(toolType == null ? null : AgentToolType.valueOf(toolType));
        audit.setRequestPayload(resultSet.getString("request_summary"));
        audit.setRequestFingerprint(resultSet.getString("request_fingerprint"));
        audit.setResponseSummary(resultSet.getString("response_summary"));
        audit.setStatus(AgentToolCallStatus.valueOf(resultSet.getString("status")));
        audit.setErrorMessage(resultSet.getString("error_message"));
        audit.setLatencyMs(resultSet.getLong("latency_ms"));
        audit.setCreatedAt(resultSet.getObject("created_at", OffsetDateTime.class));
        return audit;
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
