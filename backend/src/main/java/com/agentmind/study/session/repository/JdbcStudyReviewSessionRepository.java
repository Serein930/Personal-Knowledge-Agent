package com.agentmind.study.session.repository;

import com.agentmind.study.session.model.StudyReviewSession;
import com.agentmind.study.session.model.StudyReviewSessionItem;
import com.agentmind.study.session.model.StudyReviewSessionItemStatus;
import com.agentmind.study.session.model.StudyReviewSessionStatus;
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
 * 复习会话 PostgreSQL 适配器。
 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.agent.persistence", name = "store", havingValue = "jdbc")
public class JdbcStudyReviewSessionRepository implements StudyReviewSessionRepository {

    private static final String SESSION_COLUMNS = "id, owner_user_id, workspace_id, status, total_cards, "
            + "reviewed_cards, correct_cards, started_at, paused_at, completed_at, abandoned_at, "
            + "created_at, updated_at";
    private static final String ITEM_COLUMNS = "id, owner_user_id, workspace_id, session_id, flashcard_id, "
            + "position, status, score, reviewed_at, created_at";

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<StudyReviewSession> sessionMapper = this::mapSession;
    private final RowMapper<StudyReviewSessionItem> itemMapper = this::mapItem;

    public JdbcStudyReviewSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public StudyReviewSession create(StudyReviewSession session, List<StudyReviewSessionItem> items) {
        Long sessionId = jdbcTemplate.queryForObject("""
                insert into study_review_sessions (
                    owner_user_id, workspace_id, status, total_cards, reviewed_cards, correct_cards,
                    started_at, paused_at, completed_at, abandoned_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id
                """, Long.class,
                session.ownerUserId(), session.workspaceId(), session.status().name(), session.totalCards(),
                session.reviewedCards(), session.correctCards(), session.startedAt(), session.pausedAt(),
                session.completedAt(), session.abandonedAt(), session.createdAt(), session.updatedAt());
        for (StudyReviewSessionItem item : items) {
            jdbcTemplate.update("""
                    insert into study_review_session_items (
                        owner_user_id, workspace_id, session_id, flashcard_id, position,
                        status, score, reviewed_at, created_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    item.ownerUserId(), item.workspaceId(), sessionId, item.flashcardId(), item.position(),
                    item.status().name(), item.score(), item.reviewedAt(), item.createdAt());
        }
        return session.withId(sessionId);
    }

    @Override
    public Optional<StudyReviewSession> findByScopeAndId(Long ownerUserId, Long workspaceId, Long sessionId) {
        return jdbcTemplate.query(
                        "select " + SESSION_COLUMNS + " from study_review_sessions "
                                + "where owner_user_id = ? and workspace_id = ? and id = ?",
                        sessionMapper,
                        ownerUserId,
                        workspaceId,
                        sessionId
                ).stream()
                .findFirst();
    }

    @Override
    public List<StudyReviewSession> findByScope(Long ownerUserId, Long workspaceId, int offset, int limit) {
        return jdbcTemplate.query(
                "select " + SESSION_COLUMNS + " from study_review_sessions "
                        + "where owner_user_id = ? and workspace_id = ? "
                        + "order by started_at desc, id desc offset ? limit ?",
                sessionMapper, ownerUserId, workspaceId, offset, limit
        );
    }

    @Override
    public long countByScope(Long ownerUserId, Long workspaceId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from study_review_sessions where owner_user_id = ? and workspace_id = ?",
                Long.class, ownerUserId, workspaceId
        );
        return count == null ? 0 : count;
    }

    @Override
    public List<StudyReviewSessionItem> findItemsByScopeAndSessionId(
            Long ownerUserId,
            Long workspaceId,
            Long sessionId
    ) {
        return jdbcTemplate.query(
                "select " + ITEM_COLUMNS + " from study_review_session_items "
                        + "where owner_user_id = ? and workspace_id = ? and session_id = ? order by position",
                itemMapper,
                ownerUserId,
                workspaceId,
                sessionId
        );
    }

    @Override
    public StudyReviewSession markReviewed(
            Long ownerUserId,
            Long workspaceId,
            Long sessionId,
            Long flashcardId,
            int score,
            OffsetDateTime reviewedAt
    ) {
        int changed = jdbcTemplate.update("""
                update study_review_session_items
                set status = 'REVIEWED', score = ?, reviewed_at = ?
                where owner_user_id = ? and workspace_id = ? and session_id = ?
                  and flashcard_id = ? and status = 'PENDING'
                """, score, reviewedAt, ownerUserId, workspaceId, sessionId, flashcardId);
        if (changed == 0) {
            return findByScopeAndId(ownerUserId, workspaceId, sessionId)
                    .orElseThrow(() -> new IllegalStateException("复习会话不存在"));
        }
        return jdbcTemplate.query("""
                        update study_review_sessions
                        set reviewed_cards = reviewed_cards + 1,
                            correct_cards = correct_cards + case when ? >= 3 then 1 else 0 end,
                            status = case when reviewed_cards + 1 >= total_cards then 'COMPLETED' else status end,
                            completed_at = case when reviewed_cards + 1 >= total_cards then ? else completed_at end,
                            updated_at = ?
                        where owner_user_id = ? and workspace_id = ? and id = ? and status = 'IN_PROGRESS'
                        returning %s
                        """.formatted(SESSION_COLUMNS),
                        sessionMapper,
                        score,
                        reviewedAt,
                        reviewedAt,
                        ownerUserId,
                        workspaceId,
                        sessionId
                ).stream()
                .findFirst()
                .orElseGet(() -> findByScopeAndId(ownerUserId, workspaceId, sessionId)
                        .orElseThrow(() -> new IllegalStateException("复习会话不存在")));
    }

    @Override
    public Optional<StudyReviewSession> transitionStatus(
            Long ownerUserId,
            Long workspaceId,
            Long sessionId,
            StudyReviewSessionStatus expectedStatus,
            StudyReviewSessionStatus nextStatus,
            OffsetDateTime changedAt
    ) {
        return jdbcTemplate.query("""
                        update study_review_sessions
                        set status = ?,
                            paused_at = case when ? = 'PAUSED' then ? else paused_at end,
                            abandoned_at = case when ? = 'ABANDONED' then ? else abandoned_at end,
                            updated_at = ?
                        where owner_user_id = ? and workspace_id = ? and id = ? and status = ?
                        returning %s
                        """.formatted(SESSION_COLUMNS), sessionMapper,
                nextStatus.name(), nextStatus.name(), changedAt,
                nextStatus.name(), changedAt, changedAt,
                ownerUserId, workspaceId, sessionId, expectedStatus.name()).stream().findFirst();
    }

    private StudyReviewSession mapSession(ResultSet resultSet, int rowNumber) throws SQLException {
        return new StudyReviewSession(
                resultSet.getLong("id"), resultSet.getLong("owner_user_id"), resultSet.getLong("workspace_id"),
                StudyReviewSessionStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("total_cards"), resultSet.getInt("reviewed_cards"),
                resultSet.getInt("correct_cards"),
                resultSet.getObject("started_at", OffsetDateTime.class),
                resultSet.getObject("paused_at", OffsetDateTime.class),
                resultSet.getObject("completed_at", OffsetDateTime.class),
                resultSet.getObject("abandoned_at", OffsetDateTime.class),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private StudyReviewSessionItem mapItem(ResultSet resultSet, int rowNumber) throws SQLException {
        Integer score = resultSet.getObject("score", Integer.class);
        return new StudyReviewSessionItem(
                resultSet.getLong("id"), resultSet.getLong("owner_user_id"), resultSet.getLong("workspace_id"),
                resultSet.getLong("session_id"), resultSet.getLong("flashcard_id"),
                resultSet.getInt("position"),
                StudyReviewSessionItemStatus.valueOf(resultSet.getString("status")), score,
                resultSet.getObject("reviewed_at", OffsetDateTime.class),
                resultSet.getObject("created_at", OffsetDateTime.class)
        );
    }
}
