package com.agentmind.study.maintenance.controller;

import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.response.ApiResponse;
import com.agentmind.study.maintenance.model.dto.StudyMaintenanceStatusResponse;
import com.agentmind.study.maintenance.service.StudyMaintenanceApplicationService;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 学习维护手工触发与最近运行状态接口。 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/study/maintenance")
public class StudyMaintenanceController {

    private final StudyMaintenanceApplicationService service;

    public StudyMaintenanceController(StudyMaintenanceApplicationService service) {
        this.service = service;
    }

    @PostMapping("/run")
    public ApiResponse<StudyMaintenanceStatusResponse> run(
            @PathVariable @Positive Long workspaceId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1") @Positive Long ownerUserId
    ) {
        return ApiResponse.success(service.runNow(context(ownerUserId, workspaceId)));
    }

    @GetMapping("/status")
    public ApiResponse<StudyMaintenanceStatusResponse> status(
            @PathVariable @Positive Long workspaceId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1") @Positive Long ownerUserId
    ) {
        return ApiResponse.success(service.status(context(ownerUserId, workspaceId)));
    }

    private AgentToolExecutionContext context(Long ownerUserId, Long workspaceId) {
        return new AgentToolExecutionContext(ownerUserId, workspaceId, null);
    }
}
