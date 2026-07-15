package com.agentmind.study.plan.controller;

import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.response.ApiResponse;
import com.agentmind.study.plan.model.dto.DailyStudyPlanResponse;
import com.agentmind.study.plan.model.dto.SaveDailyStudyPlanRequest;
import com.agentmind.study.plan.service.DailyStudyPlanApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 每日学习计划接口。
 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/study-plans/daily")
public class DailyStudyPlanController {

    private final DailyStudyPlanApplicationService planService;

    public DailyStudyPlanController(DailyStudyPlanApplicationService planService) {
        this.planService = planService;
    }

    @PostMapping
    public ApiResponse<DailyStudyPlanResponse> save(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId,
            @Valid @RequestBody SaveDailyStudyPlanRequest request
    ) {
        return ApiResponse.success(planService.save(
                new AgentToolExecutionContext(ownerUserId, workspaceId, null), request
        ));
    }

    @GetMapping
    public ApiResponse<DailyStudyPlanResponse> get(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.success(planService.get(
                new AgentToolExecutionContext(ownerUserId, workspaceId, null), date
        ));
    }
}
