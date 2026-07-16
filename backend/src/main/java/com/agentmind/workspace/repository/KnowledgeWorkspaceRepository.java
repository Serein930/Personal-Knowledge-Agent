package com.agentmind.workspace.repository;

import com.agentmind.workspace.model.KnowledgeWorkspace;
import com.agentmind.workspace.model.WorkspaceMemberRole;
import java.util.Optional;
import java.util.List;

/** 知识空间与成员关系持久化端口。 */
public interface KnowledgeWorkspaceRepository {

    KnowledgeWorkspace createOwnedWorkspace(Long ownerUserId, String name, String description);

    Optional<KnowledgeWorkspace> findById(Long workspaceId);

    Optional<KnowledgeWorkspace> findFirstOwnedBy(Long ownerUserId);

    List<KnowledgeWorkspace> findAllForUser(Long userId);

    Optional<WorkspaceMemberRole> findMemberRole(Long workspaceId, Long userId);
}
