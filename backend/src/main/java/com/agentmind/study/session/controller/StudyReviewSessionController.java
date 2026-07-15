package com.agentmind.study.session.controller;

import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.response.ApiResponse;
import com.agentmind.study.flashcard.model.dto.SubmitFlashcardReviewRequest;
import com.agentmind.study.session.model.dto.CreateReviewSessionRequest;
import com.agentmind.study.session.model.dto.StudyReviewSessionResponse;
import com.agentmind.study.session.model.dto.SubmittedSessionReviewResponse;
import com.agentmind.study.session.service.StudyReviewSessionApplicationService;
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
 * 批量复习会话接口。
 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/review-sessions")
public class StudyReviewSessionController {

    private final StudyReviewSessionApplicationService sessionService;

    public StudyReviewSessionController(StudyReviewSessionApplicationService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public ApiResponse<StudyReviewSessionResponse> create(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId,
            @Valid @RequestBody CreateReviewSessionRequest request
    ) {
        return ApiResponse.success(sessionService.create(
                new AgentToolExecutionContext(ownerUserId, workspaceId, null), request
        ));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<StudyReviewSessionResponse> get(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @PathVariable @Positive(message = "复习会话编号必须为正数") Long sessionId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId
    ) {
        return ApiResponse.success(sessionService.get(
                new AgentToolExecutionContext(ownerUserId, workspaceId, null), sessionId
        ));
    }

    @PostMapping("/{sessionId}/cards/{flashcardId}/reviews")
    public ApiResponse<SubmittedSessionReviewResponse> submitReview(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @PathVariable @Positive(message = "复习会话编号必须为正数") Long sessionId,
            @PathVariable @Positive(message = "复习卡片编号必须为正数") Long flashcardId,
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "1")
            @Positive(message = "演示用户编号必须为正数") Long ownerUserId,
            @Valid @RequestBody SubmitFlashcardReviewRequest request
    ) {
        return ApiResponse.success(sessionService.submitReview(
                new AgentToolExecutionContext(ownerUserId, workspaceId, null),
                sessionId,
                flashcardId,
                request
        ));
    }
}
