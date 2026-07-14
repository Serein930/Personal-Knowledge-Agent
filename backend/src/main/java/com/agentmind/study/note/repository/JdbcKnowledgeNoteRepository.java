package com.agentmind.study.note.repository;

import com.agentmind.study.note.model.KnowledgeNote;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 知识笔记 PostgreSQL 适配器。
 *
 * <p>写入使用数据库唯一幂等键。多个实例同时处理相同请求时，只有一条笔记会被创建。</p>
 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.agent.persistence", name = "store", havingValue = "jdbc")
public class JdbcKnowledgeNoteRepository implements KnowledgeNoteRepository {

    private static final String COLUMNS = """
            id, owner_user_id, workspace_id, source_conversation_id, request_id,
            title, content, created_at, updated_at
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<KnowledgeNote> rowMapper = this::mapNote;

    public JdbcKnowledgeNoteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public KnowledgeNote save(KnowledgeNote note) {
        List<KnowledgeNote> inserted = jdbcTemplate.query("""
                insert into knowledge_notes (
                    owner_user_id, workspace_id, source_conversation_id, request_id,
                    title, content, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (owner_user_id, workspace_id, request_id) do nothing
                returning %s
                """.formatted(COLUMNS), rowMapper,
                note.ownerUserId(), note.workspaceId(), note.sourceConversationId(), note.requestId(),
                note.title(), note.content(), note.createdAt(), note.updatedAt());
        if (!inserted.isEmpty()) {
            return inserted.getFirst();
        }
        return jdbcTemplate.query(
                        "select " + COLUMNS + " from knowledge_notes "
                                + "where owner_user_id = ? and workspace_id = ? and request_id = ?",
                        rowMapper, note.ownerUserId(), note.workspaceId(), note.requestId()
                ).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("读取幂等知识笔记失败"));
    }

    @Override
    public List<KnowledgeNote> findByOwnerUserIdAndWorkspaceId(
            Long ownerUserId,
            Long workspaceId,
            int offset,
            int limit
    ) {
        return jdbcTemplate.query(
                "select " + COLUMNS + " from knowledge_notes "
                        + "where owner_user_id = ? and workspace_id = ? "
                        + "order by created_at desc, id desc offset ? limit ?",
                rowMapper, ownerUserId, workspaceId, offset, limit
        );
    }

    @Override
    public long countByOwnerUserIdAndWorkspaceId(Long ownerUserId, Long workspaceId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from knowledge_notes where owner_user_id = ? and workspace_id = ?",
                Long.class, ownerUserId, workspaceId
        );
        return count == null ? 0 : count;
    }

    private KnowledgeNote mapNote(ResultSet resultSet, int rowNumber) throws SQLException {
        return new KnowledgeNote(
                resultSet.getLong("id"),
                resultSet.getLong("owner_user_id"),
                resultSet.getLong("workspace_id"),
                nullableLong(resultSet, "source_conversation_id"),
                resultSet.getString("request_id"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }
}
