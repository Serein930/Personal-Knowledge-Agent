package com.agentmind.chat.memory.controller;

import com.agentmind.chat.memory.model.dto.ChatConversationResponse;
import com.agentmind.chat.memory.model.dto.ChatMessageResponse;
import com.agentmind.chat.memory.service.ChatMemoryQueryService;
import com.agentmind.common.response.ApiResponse;
import com.agentmind.common.response.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 短期会话记忆查询接口。
 *
 * <p>控制层只负责参数校验与统一响应包装，知识空间归属判断和分页编排由查询服务完成。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/chat/conversations")
public class ChatMemoryController {

    private final ChatMemoryQueryService queryService;

    public ChatMemoryController(ChatMemoryQueryService queryService) {
        this.queryService = queryService;
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
}
