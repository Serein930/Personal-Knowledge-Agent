package com.agentmind.study.flashcard.fsrs.repository;

import com.agentmind.study.flashcard.fsrs.model.FsrsCardSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** FSRS 卡片快照 PostgreSQL 适配器。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.agent.persistence", name = "store", havingValue = "jdbc")
public class JdbcFsrsCardSnapshotRepository implements FsrsCardSnapshotRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcFsrsCardSnapshotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<FsrsCardSnapshot> findByScopeAndFlashcardId(
            Long ownerUserId,
            Long workspaceId,
            Long flashcardId
    ) {
        return jdbcTemplate.query("""
                        select owner_user_id, workspace_id, flashcard_id, algorithm_version,
                               schema_version, profile_version, payload::text, created_at, updated_at
                        from study_flashcard_fsrs_states
                        where owner_user_id = ? and workspace_id = ? and flashcard_id = ?
                        """, this::mapSnapshot, ownerUserId, workspaceId, flashcardId)
                .stream()
                .findFirst();
    }

    @Override
    public FsrsCardSnapshot save(FsrsCardSnapshot snapshot) {
        return jdbcTemplate.query("""
                        insert into study_flashcard_fsrs_states (
                            owner_user_id, workspace_id, flashcard_id, algorithm_version,
                            schema_version, profile_version, payload, created_at, updated_at
                        ) values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?)
                        on conflict (owner_user_id, workspace_id, flashcard_id) do update
                        set algorithm_version = excluded.algorithm_version,
                            schema_version = excluded.schema_version,
                            profile_version = excluded.profile_version,
                            payload = excluded.payload,
                            updated_at = excluded.updated_at
                        returning owner_user_id, workspace_id, flashcard_id, algorithm_version,
                                  schema_version, profile_version, payload::text, created_at, updated_at
                        """, this::mapSnapshot,
                snapshot.ownerUserId(), snapshot.workspaceId(), snapshot.flashcardId(),
                snapshot.algorithmVersion(), snapshot.schemaVersion(), snapshot.profileVersion(), snapshot.payload(),
                snapshot.createdAt(), snapshot.updatedAt()).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("保存 FSRS 卡片快照失败"));
    }

    private FsrsCardSnapshot mapSnapshot(ResultSet resultSet, int rowNumber) throws SQLException {
        return new FsrsCardSnapshot(
                resultSet.getLong("owner_user_id"), resultSet.getLong("workspace_id"),
                resultSet.getLong("flashcard_id"), resultSet.getString("algorithm_version"),
                resultSet.getInt("schema_version"), resultSet.getLong("profile_version"),
                resultSet.getString("payload"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
