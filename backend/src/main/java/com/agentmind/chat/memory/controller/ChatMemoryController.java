package com.agentmind.chat.memory.controller;

import com.agentmind.chat.memory.model.dto.ChatConversationResponse;
import com.agentmind.chat.memory.model.dto.ChatMessageResponse;
import com.agentmind.chat.memory.model.dto.RenameChatConversationRequest;
import com.agentmind.chat.memory.service.ChatConversationManagementService;
import com.agentmind.chat.memory.service.ChatMemoryQueryService;
import com.agentmind.common.response.ApiResponse;
import com.agentmind.common.response.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 短期会话记忆查询与管理接口。
 *
 * <p>控制层只负责参数校验与统一响应包装，知识空间归属判断、分页编排和生命周期规则由服务层完成。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/chat/conversations")
public class ChatMemoryController {

    private final ChatMemoryQueryService queryService;
    private final ChatConversationManagementService managementService;

    public ChatMemoryController(
            ChatMemoryQueryService queryService,
            ChatConversationManagementService managementService
    ) {
        this.queryService = queryService;
        this.managementService = managementService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ChatConversationResponse>> listConversations(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于 0")
            @Max(value = 10_000, message = "页码不能大于 10000") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页数量必须大于 0")
            @Max(value = 100, message = "每页数量不能大于 100") int pageSize
    ) {
        return ApiResponse.success(queryService.listConversations(workspaceId, page, pageSize));
    }

    @GetMapping("/{conversationId}/messages")
    public ApiResponse<PageResponse<ChatMessageResponse>> listMessages(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @PathVariable @Positive(message = "会话编号必须为正数") Long conversationId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于 0")
            @Max(value = 10_000, message = "页码不能大于 10000") int page,
            @RequestParam(defaultValue = "50") @Min(value = 1, message = "每页数量必须大于 0")
            @Max(value = 100, message = "每页数量不能大于 100") int pageSize
    ) {
        return ApiResponse.success(queryService.listMessages(
                workspaceId,
                conversationId,
                page,
                pageSize
        ));
    }

    @PatchMapping("/{conversationId}")
    public ApiResponse<ChatConversationResponse> renameConversation(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @PathVariable @Positive(message = "会话编号必须为正数") Long conversationId,
            @Valid @RequestBody RenameChatConversationRequest request
    ) {
        return ApiResponse.success(managementService.rename(workspaceId, conversationId, request.title()));
    }

    @PostMapping("/{conversationId}/archive")
    public ApiResponse<ChatConversationResponse> archiveConversation(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @PathVariable @Positive(message = "会话编号必须为正数") Long conversationId
    ) {
        return ApiResponse.success(managementService.archive(workspaceId, conversationId));
    }

    @DeleteMapping("/{conversationId}")
    public ApiResponse<Void> deleteConversation(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @PathVariable @Positive(message = "会话编号必须为正数") Long conversationId
    ) {
        managementService.delete(workspaceId, conversationId);
        return ApiResponse.success("会话已删除", null);
    }
}
