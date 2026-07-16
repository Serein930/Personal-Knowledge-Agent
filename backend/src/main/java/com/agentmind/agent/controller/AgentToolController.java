package com.agentmind.agent.controller;

import com.agentmind.agent.model.dto.AgentToolExecutionRequest;
import com.agentmind.agent.model.dto.AgentToolExecutionResponse;
import com.agentmind.agent.service.AgentToolCallingOrchestrator;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.agent.audit.model.dto.AgentToolCallSummaryResponse;
import com.agentmind.common.response.ApiResponse;
import com.agentmind.common.response.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.agentmind.common.security.CurrentUserId;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 智能体工具调用与审计查询接口。
 *
 * <p>用户编号统一由认证令牌解析，不接受客户端自行声明身份。
 * 正式接入认证后应删除该请求头，改为从 Spring Security 认证主体生成执行上下文。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/agent/tool-calls")
public class AgentToolController {

    private final AgentToolCallingOrchestrator toolCallingOrchestrator;

    public AgentToolController(AgentToolCallingOrchestrator toolCallingOrchestrator) {
        this.toolCallingOrchestrator = toolCallingOrchestrator;
    }

    @PostMapping
    public ApiResponse<AgentToolExecutionResponse> execute(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @CurrentUserId
            @Positive(message = "当前用户编号必须为正数") Long ownerUserId,
            @Valid @RequestBody AgentToolExecutionRequest request
    ) {
        return ApiResponse.success(toolCallingOrchestrator.execute(
                new AgentToolExecutionContext(ownerUserId, workspaceId, request.conversationId()),
                request
        ));
    }

    @GetMapping
    public ApiResponse<PageResponse<AgentToolCallSummaryResponse>> listAudits(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @CurrentUserId
            @Positive(message = "当前用户编号必须为正数") Long ownerUserId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页数量必须大于0")
            @Max(value = 100, message = "每页数量不能大于100") int pageSize
    ) {
        return ApiResponse.success(toolCallingOrchestrator.listAudits(
                new AgentToolExecutionContext(ownerUserId, workspaceId, null),
                page,
                pageSize
        ));
    }
}
