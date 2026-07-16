package com.agentmind.workspace.controller;

import com.agentmind.common.response.ApiResponse;
import com.agentmind.common.security.CurrentUserId;
import com.agentmind.workspace.model.dto.CreateWorkspaceRequest;
import com.agentmind.workspace.model.dto.WorkspaceResponse;
import com.agentmind.workspace.service.KnowledgeWorkspaceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前用户知识空间管理接口。 */
@RestController
@RequestMapping("/api/v1/workspaces")
public class KnowledgeWorkspaceController {

    private final KnowledgeWorkspaceService workspaceService;

    public KnowledgeWorkspaceController(KnowledgeWorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public ApiResponse<List<WorkspaceResponse>> list(@CurrentUserId Long userId) {
        return ApiResponse.success(workspaceService.list(userId));
    }

    @PostMapping
    public ApiResponse<WorkspaceResponse> create(
            @CurrentUserId Long userId,
            @Valid @RequestBody CreateWorkspaceRequest request
    ) {
        return ApiResponse.success(workspaceService.create(userId, request));
    }
}
