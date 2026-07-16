package com.agentmind.study.plan.controller;

import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.response.ApiResponse;
import com.agentmind.study.plan.model.dto.DailyStudyTaskEventResponse;
import com.agentmind.study.plan.model.dto.DailyStudyTaskResponse;
import com.agentmind.study.plan.model.dto.RescheduleDailyStudyTaskRequest;
import com.agentmind.study.plan.model.dto.SubmitDailyStudyTaskFeedbackRequest;
import com.agentmind.study.plan.model.dto.UpdateDailyStudyTaskRequest;
import com.agentmind.study.plan.service.DailyStudyTaskApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.agentmind.common.security.CurrentUserId;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 每日学习任务执行接口。 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/study-tasks")
public class DailyStudyTaskController {

    private final DailyStudyTaskApplicationService service;

    public DailyStudyTaskController(DailyStudyTaskApplicationService service) {
        this.service = service;
    }

    @PostMapping("/{taskId}/complete")
    public ApiResponse<DailyStudyTaskResponse> complete(
            @PathVariable @Positive Long workspaceId,
            @PathVariable @Positive Long taskId,
            @CurrentUserId @Positive Long ownerUserId,
            @Valid @RequestBody UpdateDailyStudyTaskRequest request
    ) {
        return ApiResponse.success(service.complete(context(ownerUserId, workspaceId), taskId, request));
    }

    @PostMapping("/{taskId}/skip")
    public ApiResponse<DailyStudyTaskResponse> skip(
            @PathVariable @Positive Long workspaceId,
            @PathVariable @Positive Long taskId,
            @CurrentUserId @Positive Long ownerUserId,
            @Valid @RequestBody UpdateDailyStudyTaskRequest request
    ) {
        return ApiResponse.success(service.skip(context(ownerUserId, workspaceId), taskId, request));
    }

    @PostMapping("/{taskId}/reschedule")
    public ApiResponse<DailyStudyTaskResponse> reschedule(
            @PathVariable @Positive Long workspaceId,
            @PathVariable @Positive Long taskId,
            @CurrentUserId @Positive Long ownerUserId,
            @Valid @RequestBody RescheduleDailyStudyTaskRequest request
    ) {
        return ApiResponse.success(service.reschedule(context(ownerUserId, workspaceId), taskId, request));
    }

    @PostMapping("/{taskId}/feedback")
    public ApiResponse<DailyStudyTaskResponse> feedback(
            @PathVariable @Positive Long workspaceId,
            @PathVariable @Positive Long taskId,
            @CurrentUserId @Positive Long ownerUserId,
            @Valid @RequestBody SubmitDailyStudyTaskFeedbackRequest request
    ) {
        return ApiResponse.success(service.feedback(context(ownerUserId, workspaceId), taskId, request));
    }

    @GetMapping("/{taskId}/events")
    public ApiResponse<List<DailyStudyTaskEventResponse>> events(
            @PathVariable @Positive Long workspaceId,
            @PathVariable @Positive Long taskId,
            @CurrentUserId @Positive Long ownerUserId
    ) {
        return ApiResponse.success(service.listEvents(context(ownerUserId, workspaceId), taskId));
    }

    private AgentToolExecutionContext context(Long ownerUserId, Long workspaceId) {
        return new AgentToolExecutionContext(ownerUserId, workspaceId, null);
    }
}
