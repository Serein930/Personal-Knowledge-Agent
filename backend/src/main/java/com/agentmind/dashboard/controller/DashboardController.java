package com.agentmind.dashboard.controller;

import com.agentmind.common.response.ApiResponse;
import com.agentmind.common.security.CurrentUserId;
import com.agentmind.dashboard.model.dto.DashboardOverviewResponse;
import com.agentmind.dashboard.service.DashboardApplicationService;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 工作台聚合查询接口，只负责传输层参数校验与统一响应包装。 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/dashboard")
public class DashboardController {

    private final DashboardApplicationService dashboardApplicationService;

    public DashboardController(DashboardApplicationService dashboardApplicationService) {
        this.dashboardApplicationService = dashboardApplicationService;
    }

    @GetMapping
    public ApiResponse<DashboardOverviewResponse> getOverview(
            @CurrentUserId Long ownerUserId,
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId
    ) {
        return ApiResponse.success(dashboardApplicationService.getOverview(ownerUserId, workspaceId));
    }
}
