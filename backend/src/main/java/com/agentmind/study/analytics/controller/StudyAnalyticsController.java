package com.agentmind.study.analytics.controller;

import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.response.ApiResponse;
import com.agentmind.study.analytics.model.dto.StudyTrendResponse;
import com.agentmind.study.analytics.service.StudyTrendApplicationService;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 每日与每周学习趋势接口。 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/study/analytics")
public class StudyAnalyticsController {

    private final StudyTrendApplicationService trendService;

    public StudyAnalyticsController(StudyTrendApplicationService trendService) {
        this.trendService = trendService;
    }

    @GetMapping("/trends")
    public ApiResponse<StudyTrendResponse> trends(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ApiResponse.success(trendService.get(
                new AgentToolExecutionContext(ownerUserId, workspaceId, null), from, to
        ));
    }
}
