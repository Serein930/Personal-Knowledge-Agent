package com.agentmind.workspace.service;

import com.agentmind.workspace.model.KnowledgeWorkspace;
import com.agentmind.workspace.model.dto.CreateWorkspaceRequest;
import com.agentmind.workspace.model.dto.WorkspaceResponse;
import com.agentmind.workspace.repository.KnowledgeWorkspaceRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 当前用户知识空间管理用例。 */
@Service
public class KnowledgeWorkspaceService {

    private final KnowledgeWorkspaceRepository repository;

    public KnowledgeWorkspaceService(KnowledgeWorkspaceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public WorkspaceResponse create(Long userId, CreateWorkspaceRequest request) {
        KnowledgeWorkspace workspace = repository.createOwnedWorkspace(
                userId, request.name().trim(), request.description() == null ? "" : request.description().trim());
        return toResponse(workspace);
    }

    public List<WorkspaceResponse> list(Long userId) {
        return repository.findAllForUser(userId).stream().map(this::toResponse).toList();
    }

    private WorkspaceResponse toResponse(KnowledgeWorkspace workspace) {
        return new WorkspaceResponse(workspace.getId(), workspace.getName(), workspace.getDescription(),
                workspace.getVisibility(), workspace.getCreatedAt(), workspace.getUpdatedAt());
    }
}
