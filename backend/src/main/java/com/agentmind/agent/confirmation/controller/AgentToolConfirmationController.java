package com.agentmind.agent.confirmation.controller;

import com.agentmind.agent.confirmation.model.dto.AgentToolConfirmationResponse;
import com.agentmind.agent.confirmation.model.dto.AgentToolConfirmationTokenRequest;
import com.agentmind.agent.confirmation.model.dto.CreateAgentToolConfirmationRequest;
import com.agentmind.agent.confirmation.model.dto.CreatedAgentToolConfirmationResponse;
import com.agentmind.agent.confirmation.model.dto.DecidedAgentToolConfirmationResponse;
import com.agentmind.agent.confirmation.service.AgentToolConfirmationApplicationService;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 写工具确认单接口。
 *
 * <p>前端先创建确认单并展示参数摘要，用户明确确认后再携带一次性令牌调用确认接口。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/agent/write-tool-confirmations")
public class AgentToolConfirmationController {

    private final AgentToolConfirmationApplicationService confirmationService;

    public AgentToolConfirmationController(AgentToolConfirmationApplicationService confirmationService) {
        this.confirmationService = confirmationService;
    }

    @PostMapping
    public ApiResponse<CreatedAgentToolConfirmationResponse> create(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId,
            @Valid @RequestBody CreateAgentToolConfirmationRequest request
    ) {
        return ApiResponse.success(confirmationService.create(
                new AgentToolExecutionContext(
                        ownerUserId, workspaceId, request.conversationId(), request.messageId()
                ),
                request
        ));
    }

    @GetMapping("/{confirmationId}")
    public ApiResponse<AgentToolConfirmationResponse> get(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @PathVariable @Positive(message = "确认单编号必须为正数") Long confirmationId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId
    ) {
        return ApiResponse.success(confirmationService.get(ownerUserId, workspaceId, confirmationId));
    }

    @PostMapping("/{confirmationId}/confirm")
    public ApiResponse<DecidedAgentToolConfirmationResponse> confirm(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @PathVariable @Positive(message = "确认单编号必须为正数") Long confirmationId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId,
            @Valid @RequestBody AgentToolConfirmationTokenRequest request
    ) {
        return ApiResponse.success(confirmationService.confirm(
                ownerUserId, workspaceId, confirmationId, request.confirmationToken()
        ));
    }

    @PostMapping("/{confirmationId}/reject")
    public ApiResponse<DecidedAgentToolConfirmationResponse> reject(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @PathVariable @Positive(message = "确认单编号必须为正数") Long confirmationId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId,
            @Valid @RequestBody AgentToolConfirmationTokenRequest request
    ) {
        return ApiResponse.success(confirmationService.reject(
                ownerUserId, workspaceId, confirmationId, request.confirmationToken()
        ));
    }
}
