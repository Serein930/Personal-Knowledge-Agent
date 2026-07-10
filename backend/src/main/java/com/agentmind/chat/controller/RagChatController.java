package com.agentmind.chat.controller;

import com.agentmind.chat.model.dto.RagChatRequest;
import com.agentmind.chat.model.dto.RagChatResponse;
import com.agentmind.chat.service.RagContextAssemblyService;
import com.agentmind.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG chat API contract.
 *
 * <p>The endpoint currently prepares retrieval context only. It gives the frontend a stable response structure with
 * citations before the project introduces Spring AI chat generation and streaming responses.</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/rag")
public class RagChatController {

    private final RagContextAssemblyService ragContextAssemblyService;

    public RagChatController(RagContextAssemblyService ragContextAssemblyService) {
        this.ragContextAssemblyService = ragContextAssemblyService;
    }

    @PostMapping("/chat")
    public ApiResponse<RagChatResponse> chat(
            @PathVariable @Positive(message = "workspaceId must be positive") Long workspaceId,
            @Valid @RequestBody RagChatRequest request
    ) {
        return ApiResponse.success(ragContextAssemblyService.prepareChatContext(workspaceId, request));
    }
}
