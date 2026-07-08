package com.agentmind.knowledge.controller;

import com.agentmind.common.response.ApiResponse;
import com.agentmind.knowledge.model.dto.KnowledgeSearchRequest;
import com.agentmind.knowledge.model.dto.KnowledgeSearchResponse;
import com.agentmind.knowledge.service.KnowledgeSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Temporary semantic search API.
 *
 * <p>This endpoint exposes the retrieval skeleton before RAG chat is implemented. It lets the frontend and backend
 * verify whether uploaded/captured chunks are indexed and searched within the selected workspace.</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/knowledge")
public class KnowledgeSearchController {

    private final KnowledgeSearchService knowledgeSearchService;

    public KnowledgeSearchController(KnowledgeSearchService knowledgeSearchService) {
        this.knowledgeSearchService = knowledgeSearchService;
    }

    @PostMapping("/search")
    public ApiResponse<KnowledgeSearchResponse> search(
            @PathVariable @Positive(message = "workspaceId must be positive") Long workspaceId,
            @Valid @RequestBody KnowledgeSearchRequest request
    ) {
        return ApiResponse.success(knowledgeSearchService.search(workspaceId, request.query(), request.topK()));
    }
}
