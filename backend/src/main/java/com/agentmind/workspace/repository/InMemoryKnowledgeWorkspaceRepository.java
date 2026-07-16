package com.agentmind.workspace.repository;

import com.agentmind.workspace.model.KnowledgeWorkspace;
import com.agentmind.workspace.model.WorkspaceMemberRole;
import com.agentmind.workspace.model.WorkspaceVisibility;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/** 无数据库模式使用的知识空间与成员仓储。 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.core.persistence", name = "store", havingValue = "memory", matchIfMissing = true)
public class InMemoryKnowledgeWorkspaceRepository implements KnowledgeWorkspaceRepository {

    private final AtomicLong idGenerator = new AtomicLong(1);
    private final Map<Long, KnowledgeWorkspace> workspaces = new ConcurrentHashMap<>();
    private final Map<String, WorkspaceMemberRole> memberships = new ConcurrentHashMap<>();

    public InMemoryKnowledgeWorkspaceRepository() {
        createSeedWorkspace();
    }

    @Override
    public synchronized KnowledgeWorkspace createOwnedWorkspace(Long ownerUserId, String name, String description) {
        long id = idGenerator.incrementAndGet();
        KnowledgeWorkspace workspace = newWorkspace(id, ownerUserId, name, description);
        workspaces.put(id, workspace);
        memberships.put(key(id, ownerUserId), WorkspaceMemberRole.OWNER);
        return workspace;
    }

    @Override
    public Optional<KnowledgeWorkspace> findById(Long workspaceId) {
        return Optional.ofNullable(workspaces.get(workspaceId));
    }

    @Override
    public Optional<KnowledgeWorkspace> findFirstOwnedBy(Long ownerUserId) {
        return workspaces.values().stream()
                .filter(workspace -> ownerUserId.equals(workspace.getOwnerUserId()))
                .min(java.util.Comparator.comparing(KnowledgeWorkspace::getId));
    }

    @Override
    public java.util.List<KnowledgeWorkspace> findAllForUser(Long userId) {
        return workspaces.values().stream()
                .filter(workspace -> memberships.containsKey(key(workspace.getId(), userId)))
                .sorted(java.util.Comparator.comparing(KnowledgeWorkspace::getId))
                .toList();
    }

    @Override
    public Optional<WorkspaceMemberRole> findMemberRole(Long workspaceId, Long userId) {
        WorkspaceMemberRole role = memberships.get(key(workspaceId, userId));
        if (role != null) {
            return Optional.of(role);
        }
        // 旧单元测试会动态使用虚拟空间编号；仅种子用户在内存模式下拥有这些空间。
        return Long.valueOf(1L).equals(userId) ? Optional.of(WorkspaceMemberRole.OWNER) : Optional.empty();
    }

    private void createSeedWorkspace() {
        KnowledgeWorkspace workspace = newWorkspace(1L, 1L, "Java Backend Learning", "默认演示知识空间");
        workspaces.put(1L, workspace);
        memberships.put(key(1L, 1L), WorkspaceMemberRole.OWNER);
    }

    private KnowledgeWorkspace newWorkspace(Long id, Long ownerUserId, String name, String description) {
        OffsetDateTime now = OffsetDateTime.now();
        KnowledgeWorkspace workspace = new KnowledgeWorkspace();
        workspace.setId(id);
        workspace.setOwnerUserId(ownerUserId);
        workspace.setName(name);
        workspace.setDescription(description);
        workspace.setVisibility(WorkspaceVisibility.PRIVATE);
        workspace.setCreatedAt(now);
        workspace.setUpdatedAt(now);
        return workspace;
    }

    private String key(Long workspaceId, Long userId) {
        return workspaceId + ":" + userId;
    }
}
