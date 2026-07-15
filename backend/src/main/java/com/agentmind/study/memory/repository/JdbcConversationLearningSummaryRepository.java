package com.agentmind.study.memory.repository;

import com.agentmind.study.memory.model.ConversationLearningSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 长期会话摘要 PostgreSQL 适配器。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.agent.persistence", name = "store", havingValue = "jdbc")
public class JdbcConversationLearningSummaryRepository implements ConversationLearningSummaryRepository {

    private static final String COLUMNS = "id, owner_user_id, workspace_id, conversation_id, summary, "
            + "topics::text, weak_topics::text, message_count, version, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcConversationLearningSummaryRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public ConversationLearningSummary saveOrUpdate(ConversationLearningSummary summary) {
        return jdbcTemplate.query("""
                        insert into conversation_learning_summaries (
                            owner_user_id, workspace_id, conversation_id, summary, topics, weak_topics,
                            message_count, version, created_at, updated_at
                        ) values (?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, 0, ?, ?)
                        on conflict (owner_user_id, workspace_id, conversation_id) do update
                        set summary = excluded.summary, topics = excluded.topics, weak_topics = excluded.weak_topics,
                            message_count = excluded.message_count,
                            version = conversation_learning_summaries.version + 1,
                            updated_at = excluded.updated_at
                        returning %s
                        """.formatted(COLUMNS), this::mapSummary,
                summary.ownerUserId(), summary.workspaceId(), summary.conversationId(), summary.summary(),
                writeList(summary.topics()), writeList(summary.weakTopics()), summary.messageCount(),
                summary.createdAt(), summary.updatedAt()).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("保存长期会话摘要失败"));
    }

    @Override
    public List<ConversationLearningSummary> findByScope(Long ownerUserId, Long workspaceId, int limit) {
        return jdbcTemplate.query("select " + COLUMNS + " from conversation_learning_summaries "
                        + "where owner_user_id = ? and workspace_id = ? order by updated_at desc, id desc limit ?",
                this::mapSummary, ownerUserId, workspaceId, limit);
    }

    private ConversationLearningSummary mapSummary(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ConversationLearningSummary(
                resultSet.getLong("id"), resultSet.getLong("owner_user_id"),
                resultSet.getLong("workspace_id"), resultSet.getLong("conversation_id"),
                resultSet.getString("summary"), readList(resultSet, "topics"),
                readList(resultSet, "weak_topics"), resultSet.getInt("message_count"),
                resultSet.getLong("version"), resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private String writeList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化长期会话摘要主题失败", exception);
        }
    }

    private List<String> readList(ResultSet resultSet, String column) throws SQLException {
        try {
            return objectMapper.readValue(resultSet.getString(column), new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new SQLException("读取长期会话摘要主题失败", exception);
        }
    }
}
