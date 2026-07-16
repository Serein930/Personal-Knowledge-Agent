package com.agentmind.workspace.repository;

import com.agentmind.workspace.model.KnowledgeWorkspace;
import com.agentmind.workspace.model.WorkspaceMemberRole;
import com.agentmind.workspace.model.WorkspaceVisibility;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** PostgreSQL 知识空间仓储，创建空间和所有者成员关系应由上层事务包裹。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.core.persistence", name = "store", havingValue = "jdbc")
public class JdbcKnowledgeWorkspaceRepository implements KnowledgeWorkspaceRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcKnowledgeWorkspaceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public KnowledgeWorkspace createOwnedWorkspace(Long ownerUserId, String name, String description) {
        OffsetDateTime now = OffsetDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    insert into knowledge_workspace (
                        owner_user_id, name, description, visibility, created_at, updated_at
                    ) values (?, ?, ?, 'PRIVATE', ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, ownerUserId);
            statement.setString(2, name);
            statement.setString(3, description);
            statement.setObject(4, now);
            statement.setObject(5, now);
            return statement;
        }, keyHolder);
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("创建知识空间后未返回主键");
        }
        jdbcTemplate.update("""
                insert into workspace_member (workspace_id, user_id, member_role, created_at)
                values (?, ?, 'OWNER', ?)
                """, id.longValue(), ownerUserId, now);
        return findById(id.longValue()).orElseThrow();
    }

    @Override
    public Optional<KnowledgeWorkspace> findById(Long workspaceId) {
        return jdbcTemplate.query("select * from knowledge_workspace where id = ? and deleted_at is null",
                this::mapWorkspace, workspaceId).stream().findFirst();
    }

    @Override
    public Optional<KnowledgeWorkspace> findFirstOwnedBy(Long ownerUserId) {
        return jdbcTemplate.query("""
                select * from knowledge_workspace
                where owner_user_id = ? and deleted_at is null
                order by id limit 1
                """, this::mapWorkspace, ownerUserId).stream().findFirst();
    }

    @Override
    public java.util.List<KnowledgeWorkspace> findAllForUser(Long userId) {
        return jdbcTemplate.query("""
                select workspace.*
                from knowledge_workspace workspace
                join workspace_member member on member.workspace_id = workspace.id
                where member.user_id = ? and workspace.deleted_at is null
                order by workspace.updated_at desc
                """, this::mapWorkspace, userId);
    }

    @Override
    public Optional<WorkspaceMemberRole> findMemberRole(Long workspaceId, Long userId) {
        return jdbcTemplate.query("""
                select member_role from workspace_member
                where workspace_id = ? and user_id = ?
                """, (resultSet, rowNumber) -> WorkspaceMemberRole.valueOf(resultSet.getString("member_role")),
                workspaceId, userId).stream().findFirst();
    }

    private KnowledgeWorkspace mapWorkspace(ResultSet resultSet, int rowNumber) throws SQLException {
        KnowledgeWorkspace workspace = new KnowledgeWorkspace();
        workspace.setId(resultSet.getLong("id"));
        workspace.setOwnerUserId(resultSet.getLong("owner_user_id"));
        workspace.setName(resultSet.getString("name"));
        workspace.setDescription(resultSet.getString("description"));
        workspace.setVisibility(WorkspaceVisibility.valueOf(resultSet.getString("visibility")));
        workspace.setDefaultModel(resultSet.getString("default_model"));
        workspace.setDefaultEmbeddingModel(resultSet.getString("default_embedding_model"));
        workspace.setCreatedAt(resultSet.getObject("created_at", OffsetDateTime.class));
        workspace.setUpdatedAt(resultSet.getObject("updated_at", OffsetDateTime.class));
        return workspace;
    }
}
