package com.agentmind.study.profile.controller;

import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.response.ApiResponse;
import com.agentmind.study.profile.model.dto.LearningTopicProfileResponse;
import com.agentmind.study.profile.service.LearningProfileApplicationService;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 学习画像查询与重建接口。 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/study/learning-profile")
public class LearningProfileController {

    private final LearningProfileApplicationService service;

    public LearningProfileController(LearningProfileApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<LearningTopicProfileResponse>> get(
            @PathVariable @Positive Long workspaceId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1") @Positive Long ownerUserId
    ) {
        return ApiResponse.success(service.get(context(ownerUserId, workspaceId)));
    }

    @PostMapping("/refresh")
    public ApiResponse<List<LearningTopicProfileResponse>> refresh(
            @PathVariable @Positive Long workspaceId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1") @Positive Long ownerUserId
    ) {
        return ApiResponse.success(service.refresh(context(ownerUserId, workspaceId)));
    }

    private AgentToolExecutionContext context(Long ownerUserId, Long workspaceId) {
        return new AgentToolExecutionContext(ownerUserId, workspaceId, null);
    }
}
