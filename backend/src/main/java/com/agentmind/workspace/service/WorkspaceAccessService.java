package com.agentmind.workspace.service;

import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.workspace.model.WorkspaceMemberRole;
import com.agentmind.workspace.repository.KnowledgeWorkspaceRepository;
import org.springframework.stereotype.Service;

/** 统一执行知识空间成员和写权限检查。 */
@Service
public class WorkspaceAccessService {

    private final KnowledgeWorkspaceRepository repository;

    public WorkspaceAccessService(KnowledgeWorkspaceRepository repository) {
        this.repository = repository;
    }

    public WorkspaceMemberRole requireReadable(Long userId, Long workspaceId) {
        return repository.findMemberRole(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "当前用户不是该知识空间成员"));
    }

    public WorkspaceMemberRole requireWritable(Long userId, Long workspaceId) {
        WorkspaceMemberRole role = requireReadable(userId, workspaceId);
        if (role == WorkspaceMemberRole.VIEWER) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前用户只有知识空间只读权限");
        }
        return role;
    }
}
