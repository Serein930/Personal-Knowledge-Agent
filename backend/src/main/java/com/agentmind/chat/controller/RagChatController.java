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
 * 检索增强生成问答接口。
 *
 * <p>该接口负责接收用户问题并返回回答、检索上下文和引用来源。真实模型调用不会放在控制层中，
 * 而是通过后端服务层的回答生成端口进行适配。</p>
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
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @Valid @RequestBody RagChatRequest request
    ) {
        return ApiResponse.success(ragContextAssemblyService.prepareChatContext(workspaceId, request));
    }
}
