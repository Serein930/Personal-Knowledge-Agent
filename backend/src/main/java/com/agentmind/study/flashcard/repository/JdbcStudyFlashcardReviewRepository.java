package com.agentmind.study.flashcard.repository;

import com.agentmind.study.flashcard.model.StudyFlashcardReview;
import com.agentmind.study.flashcard.model.StudyFlashcardStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import com.agentmind.study.maintenance.model.StudyDataScope;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 复习评分记录 PostgreSQL 适配器。
 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.agent.persistence", name = "store", havingValue = "jdbc")
public class JdbcStudyFlashcardReviewRepository implements StudyFlashcardReviewRepository {

    private static final String COLUMNS = """
            id, owner_user_id, workspace_id, flashcard_id, request_id, score,
            previous_status, next_status, previous_interval_days, next_interval_days,
            previous_ease_factor, next_ease_factor, previous_due_at, next_due_at,
            algorithm, reviewed_at, created_at
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<StudyFlashcardReview> rowMapper = this::mapReview;

    public JdbcStudyFlashcardReviewRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public StudyFlashcardReview save(StudyFlashcardReview review) {
        Long id = jdbcTemplate.queryForObject("""
                insert into study_flashcard_reviews (
                    owner_user_id, workspace_id, flashcard_id, request_id, score,
                    previous_status, next_status, previous_interval_days, next_interval_days,
                    previous_ease_factor, next_ease_factor, previous_due_at, next_due_at,
                    algorithm, reviewed_at, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning id
                """, Long.class,
                review.ownerUserId(), review.workspaceId(), review.flashcardId(), review.requestId(), review.score(),
                review.previousStatus().name(), review.nextStatus().name(), review.previousIntervalDays(),
                review.nextIntervalDays(), review.previousEaseFactor(), review.nextEaseFactor(),
                review.previousDueAt(), review.nextDueAt(), review.algorithm(), review.reviewedAt(), review.createdAt());
        return review.withId(id);
    }

    @Override
    public Optional<StudyFlashcardReview> findByOwnerUserIdAndWorkspaceIdAndRequestId(
            Long ownerUserId,
            Long workspaceId,
            String requestId
    ) {
        return jdbcTemplate.query(
                        "select " + COLUMNS + " from study_flashcard_reviews "
                                + "where owner_user_id = ? and workspace_id = ? and request_id = ?",
                        rowMapper,
                        ownerUserId,
                        workspaceId,
                        requestId
                ).stream()
                .findFirst();
    }

    @Override
    public List<StudyFlashcardReview> findByOwnerUserIdAndWorkspaceIdAndFlashcardId(
            Long ownerUserId,
            Long workspaceId,
            Long flashcardId,
            int offset,
            int limit
    ) {
        return jdbcTemplate.query(
                "select " + COLUMNS + " from study_flashcard_reviews "
                        + "where owner_user_id = ? and workspace_id = ? and flashcard_id = ? "
                        + "order by reviewed_at desc, id desc offset ? limit ?",
                rowMapper,
                ownerUserId,
                workspaceId,
                flashcardId,
                offset,
                limit
        );
    }

    @Override
    public long countByOwnerUserIdAndWorkspaceIdAndFlashcardId(
            Long ownerUserId,
            Long workspaceId,
            Long flashcardId
    ) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from study_flashcard_reviews "
                        + "where owner_user_id = ? and workspace_id = ? and flashcard_id = ?",
                Long.class,
                ownerUserId,
                workspaceId,
                flashcardId
        );
        return count == null ? 0 : count;
    }

    @Override
    public List<StudyFlashcardReview> findChronologicalByOwnerUserIdAndWorkspaceIdAndFlashcardId(
            Long ownerUserId,
            Long workspaceId,
            Long flashcardId
    ) {
        return jdbcTemplate.query(
                "select " + COLUMNS + " from study_flashcard_reviews "
                        + "where owner_user_id = ? and workspace_id = ? and flashcard_id = ? "
                        + "order by reviewed_at, id",
                rowMapper,
                ownerUserId,
                workspaceId,
                flashcardId
        );
    }

    @Override
    public List<StudyFlashcardReview> findAllByOwnerUserIdAndWorkspaceId(Long ownerUserId, Long workspaceId) {
        return jdbcTemplate.query(
                "select " + COLUMNS + " from study_flashcard_reviews "
                        + "where owner_user_id = ? and workspace_id = ? order by reviewed_at, id",
                rowMapper,
                ownerUserId,
                workspaceId
        );
    }

    @Override
    public List<StudyFlashcardReview> findAllByOwnerUserId(Long ownerUserId) {
        return jdbcTemplate.query(
                "select " + COLUMNS + " from study_flashcard_reviews "
                        + "where owner_user_id = ? order by reviewed_at, id",
                rowMapper,
                ownerUserId
        );
    }

    @Override
    public List<StudyDataScope> findActiveScopes(int limit) {
        return jdbcTemplate.query("""
                        select owner_user_id, workspace_id
                        from study_flashcard_reviews
                        group by owner_user_id, workspace_id
                        order by max(reviewed_at) desc
                        limit ?
                        """, (resultSet, rowNumber) -> new StudyDataScope(
                        resultSet.getLong("owner_user_id"), resultSet.getLong("workspace_id")
                ), limit);
    }

    private StudyFlashcardReview mapReview(ResultSet resultSet, int rowNumber) throws SQLException {
        return new StudyFlashcardReview(
                resultSet.getLong("id"),
                resultSet.getLong("owner_user_id"),
                resultSet.getLong("workspace_id"),
                resultSet.getLong("flashcard_id"),
                resultSet.getString("request_id"),
                resultSet.getInt("score"),
                StudyFlashcardStatus.valueOf(resultSet.getString("previous_status")),
                StudyFlashcardStatus.valueOf(resultSet.getString("next_status")),
                resultSet.getInt("previous_interval_days"),
                resultSet.getInt("next_interval_days"),
                resultSet.getDouble("previous_ease_factor"),
                resultSet.getDouble("next_ease_factor"),
                resultSet.getObject("previous_due_at", OffsetDateTime.class),
                resultSet.getObject("next_due_at", OffsetDateTime.class),
                resultSet.getString("algorithm"),
                resultSet.getObject("reviewed_at", OffsetDateTime.class),
                resultSet.getObject("created_at", OffsetDateTime.class)
        );
    }
}
