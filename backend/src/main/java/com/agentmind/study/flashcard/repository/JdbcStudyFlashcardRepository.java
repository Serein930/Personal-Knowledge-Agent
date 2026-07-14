package com.agentmind.study.flashcard.repository;

import com.agentmind.study.flashcard.model.StudyFlashcard;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 复习卡片 PostgreSQL 适配器。
 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.agent.persistence", name = "store", havingValue = "jdbc")
public class JdbcStudyFlashcardRepository implements StudyFlashcardRepository {

    private static final String COLUMNS = """
            id, owner_user_id, workspace_id, source_conversation_id, request_id,
            question, answer, explanation, created_at, updated_at
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<StudyFlashcard> rowMapper = this::mapFlashcard;

    public JdbcStudyFlashcardRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public StudyFlashcard save(StudyFlashcard flashcard) {
        List<StudyFlashcard> inserted = jdbcTemplate.query("""
                insert into study_flashcards (
                    owner_user_id, workspace_id, source_conversation_id, request_id,
                    question, answer, explanation, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (owner_user_id, workspace_id, request_id) do nothing
                returning %s
                """.formatted(COLUMNS), rowMapper,
                flashcard.ownerUserId(), flashcard.workspaceId(), flashcard.sourceConversationId(),
                flashcard.requestId(), flashcard.question(), flashcard.answer(), flashcard.explanation(),
                flashcard.createdAt(), flashcard.updatedAt());
        if (!inserted.isEmpty()) {
            return inserted.getFirst();
        }
        return jdbcTemplate.query(
                        "select " + COLUMNS + " from study_flashcards "
                                + "where owner_user_id = ? and workspace_id = ? and request_id = ?",
                        rowMapper, flashcard.ownerUserId(), flashcard.workspaceId(), flashcard.requestId()
                ).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("读取幂等复习卡片失败"));
    }

    @Override
    public List<StudyFlashcard> findByOwnerUserIdAndWorkspaceId(
            Long ownerUserId,
            Long workspaceId,
            int offset,
            int limit
    ) {
        return jdbcTemplate.query(
                "select " + COLUMNS + " from study_flashcards "
                        + "where owner_user_id = ? and workspace_id = ? "
                        + "order by created_at desc, id desc offset ? limit ?",
                rowMapper, ownerUserId, workspaceId, offset, limit
        );
    }

    @Override
    public long countByOwnerUserIdAndWorkspaceId(Long ownerUserId, Long workspaceId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from study_flashcards where owner_user_id = ? and workspace_id = ?",
                Long.class, ownerUserId, workspaceId
        );
        return count == null ? 0 : count;
    }

    private StudyFlashcard mapFlashcard(ResultSet resultSet, int rowNumber) throws SQLException {
        return new StudyFlashcard(
                resultSet.getLong("id"),
                resultSet.getLong("owner_user_id"),
                resultSet.getLong("workspace_id"),
                nullableLong(resultSet, "source_conversation_id"),
                resultSet.getString("request_id"),
                resultSet.getString("question"),
                resultSet.getString("answer"),
                resultSet.getString("explanation"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }
}
