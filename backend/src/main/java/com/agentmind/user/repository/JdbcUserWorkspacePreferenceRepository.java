package com.agentmind.user.repository;

import com.agentmind.user.model.CitationPolicy;
import com.agentmind.user.model.UserWorkspacePreference;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL 用户知识空间偏好仓储，使用版本条件避免并发覆盖。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.core.persistence", name = "store", havingValue = "jdbc")
public class JdbcUserWorkspacePreferenceRepository implements UserWorkspacePreferenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserWorkspacePreferenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UserWorkspacePreference> findByUserIdAndWorkspaceId(Long userId, Long workspaceId) {
        return jdbcTemplate.query("""
                select * from user_workspace_preference
                where user_id = ? and workspace_id = ?
                """, this::mapPreference, userId, workspaceId).stream().findFirst();
    }

    @Override
    public Optional<UserWorkspacePreference> save(
            UserWorkspacePreference preference,
            long expectedVersion
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        return jdbcTemplate.query("""
                insert into user_workspace_preference (
                    user_id, workspace_id, chat_model, embedding_model, citation_policy,
                    default_top_k, version, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, 1, ?, ?)
                on conflict (user_id, workspace_id) do update set
                    chat_model = excluded.chat_model,
                    embedding_model = excluded.embedding_model,
                    citation_policy = excluded.citation_policy,
                    default_top_k = excluded.default_top_k,
                    version = user_workspace_preference.version + 1,
                    updated_at = excluded.updated_at
                where user_workspace_preference.version = ?
                returning *
                """, this::mapPreference,
                preference.userId(),
                preference.workspaceId(),
                preference.chatModel(),
                preference.embeddingModel(),
                preference.citationPolicy().name(),
                preference.defaultTopK(),
                now,
                now,
                expectedVersion
        ).stream().findFirst();
    }

    private UserWorkspacePreference mapPreference(ResultSet resultSet, int rowNumber) throws SQLException {
        return new UserWorkspacePreference(
                resultSet.getLong("user_id"),
                resultSet.getLong("workspace_id"),
                resultSet.getString("chat_model"),
                resultSet.getString("embedding_model"),
                CitationPolicy.valueOf(resultSet.getString("citation_policy")),
                resultSet.getInt("default_top_k"),
                resultSet.getLong("version"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
