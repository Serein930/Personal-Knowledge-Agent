package com.agentmind.knowledge.controller;

import com.agentmind.common.response.ApiResponse;
import com.agentmind.common.security.CurrentUserId;
import com.agentmind.knowledge.model.dto.KnowledgeSearchRequest;
import com.agentmind.knowledge.model.dto.KnowledgeSearchResponse;
import com.agentmind.knowledge.service.KnowledgeSearchService;
import com.agentmind.workspace.service.WorkspaceAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 临时语义检索接口。
 *
 * <p>该接口用于在检索增强生成问答完善前暴露检索骨架，帮助前后端验证上传或采集得到的片段
 * 是否已经在当前知识空间内完成索引和检索。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/knowledge")
public class KnowledgeSearchController {

    private final KnowledgeSearchService knowledgeSearchService;
    private final WorkspaceAccessService workspaceAccessService;

    public KnowledgeSearchController(
            KnowledgeSearchService knowledgeSearchService,
            WorkspaceAccessService workspaceAccessService
    ) {
        this.knowledgeSearchService = knowledgeSearchService;
        this.workspaceAccessService = workspaceAccessService;
    }

    @PostMapping("/search")
    public ApiResponse<KnowledgeSearchResponse> search(
            @CurrentUserId Long ownerUserId,
            @PathVariable @Positive(message = "知识空间ID必须为正数") Long workspaceId,
            @Valid @RequestBody KnowledgeSearchRequest request
    ) {
        workspaceAccessService.requireReadable(ownerUserId, workspaceId);
        return ApiResponse.success(knowledgeSearchService.search(workspaceId, request.query(), request.topK()));
    }
}
