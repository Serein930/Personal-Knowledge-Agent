package com.agentmind.chat.controller;

import com.agentmind.chat.model.RagModelCallStatus;
import com.agentmind.chat.model.dto.RagModelCallObservationResponse;
import com.agentmind.chat.model.dto.RagModelCallMetricsResponse;
import com.agentmind.chat.service.RagModelCallAuditService;
import com.agentmind.chat.service.RagModelCallMetricService;
import com.agentmind.common.response.ApiResponse;
import com.agentmind.common.response.PageResponse;
import com.agentmind.common.security.CurrentUserId;
import com.agentmind.workspace.service.WorkspaceAccessService;
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
 * 模型调用观测记录查询接口。
 *
 * <p>接口按知识空间强制隔离，并提供分页和状态筛选。访问前会从安全上下文解析当前用户，
 * 再通过知识空间成员关系校验读取权限。</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/rag/model-calls")
public class RagModelCallAuditController {

    private final RagModelCallAuditService auditService;
    private final RagModelCallMetricService metricService;
    private final WorkspaceAccessService workspaceAccessService;

    public RagModelCallAuditController(
            RagModelCallAuditService auditService,
            RagModelCallMetricService metricService,
            WorkspaceAccessService workspaceAccessService
    ) {
        this.auditService = auditService;
        this.metricService = metricService;
        this.workspaceAccessService = workspaceAccessService;
    }

    @GetMapping
    public ApiResponse<PageResponse<RagModelCallObservationResponse>> listModelCalls(
            @CurrentUserId Long ownerUserId,
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于 0")
            @Max(value = 10_000, message = "页码不能大于 10000") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页数量必须大于 0")
            @Max(value = 100, message = "每页数量不能大于 100") int pageSize,
            @RequestParam(required = false) RagModelCallStatus status
    ) {
        workspaceAccessService.requireReadable(ownerUserId, workspaceId);
        return ApiResponse.success(auditService.listObservations(workspaceId, page, pageSize, status));
    }

    /**
     * 查询知识空间总体指标，以及按模型和提示词版本组合生成的分组指标。
     */
    @GetMapping("/metrics")
    public ApiResponse<RagModelCallMetricsResponse> getModelCallMetrics(
            @CurrentUserId Long ownerUserId,
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId
    ) {
        workspaceAccessService.requireReadable(ownerUserId, workspaceId);
        return ApiResponse.success(metricService.getMetrics(workspaceId));
    }
}
