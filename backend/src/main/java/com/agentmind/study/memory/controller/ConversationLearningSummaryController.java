package com.agentmind.study.memory.controller;

import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.response.ApiResponse;
import com.agentmind.study.memory.model.dto.ConversationLearningSummaryResponse;
import com.agentmind.study.memory.service.ConversationLearningSummaryService;
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

/** 长期会话学习摘要接口。 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/study/conversation-summaries")
public class ConversationLearningSummaryController {

    private final ConversationLearningSummaryService summaryService;
    private final LearningProfileApplicationService profileService;

    public ConversationLearningSummaryController(
            ConversationLearningSummaryService summaryService,
            LearningProfileApplicationService profileService
    ) {
        this.summaryService = summaryService;
        this.profileService = profileService;
    }

    @GetMapping
    public ApiResponse<List<ConversationLearningSummaryResponse>> get(
            @PathVariable @Positive Long workspaceId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1") @Positive Long ownerUserId
    ) {
        return ApiResponse.success(summaryService.get(context(ownerUserId, workspaceId)));
    }

    @PostMapping("/refresh")
    public ApiResponse<List<ConversationLearningSummaryResponse>> refresh(
            @PathVariable @Positive Long workspaceId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1") @Positive Long ownerUserId
    ) {
        AgentToolExecutionContext context = context(ownerUserId, workspaceId);
        List<String> topics = profileService.refresh(context).stream()
                .map(LearningTopicProfileResponse::topic).toList();
        return ApiResponse.success(summaryService.refresh(context, topics));
    }

    private AgentToolExecutionContext context(Long ownerUserId, Long workspaceId) {
        return new AgentToolExecutionContext(ownerUserId, workspaceId, null);
    }
}
