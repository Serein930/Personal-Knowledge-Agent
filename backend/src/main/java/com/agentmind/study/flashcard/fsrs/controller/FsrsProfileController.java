package com.agentmind.study.flashcard.fsrs.controller;

import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.response.ApiResponse;
import com.agentmind.common.response.PageResponse;
import com.agentmind.study.flashcard.fsrs.model.dto.FsrsOptimizationJobResponse;
import com.agentmind.study.flashcard.fsrs.model.dto.FsrsProfileVersionResponse;
import com.agentmind.study.flashcard.fsrs.model.dto.FsrsUserProfileResponse;
import com.agentmind.study.flashcard.fsrs.model.dto.RollbackFsrsProfileRequest;
import com.agentmind.study.flashcard.fsrs.model.dto.StartFsrsOptimizationRequest;
import com.agentmind.study.flashcard.fsrs.model.dto.UpdateFsrsUserProfileRequest;
import com.agentmind.study.flashcard.fsrs.service.FsrsOptimizationApplicationService;
import com.agentmind.study.flashcard.fsrs.service.FsrsUserProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.agentmind.common.security.CurrentUserId;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用户级 FSRS 参数及优化任务接口。 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/study/fsrs")
public class FsrsProfileController {

    private final FsrsUserProfileService profileService;
    private final FsrsOptimizationApplicationService optimizationService;

    public FsrsProfileController(
            FsrsUserProfileService profileService,
            FsrsOptimizationApplicationService optimizationService
    ) {
        this.profileService = profileService;
        this.optimizationService = optimizationService;
    }

    @GetMapping("/profile")
    public ApiResponse<FsrsUserProfileResponse> getProfile(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @CurrentUserId
            @Positive(message = "当前用户编号必须为正数") Long ownerUserId
    ) {
        return ApiResponse.success(profileService.get(context(ownerUserId, workspaceId)));
    }

    @PutMapping("/profile")
    public ApiResponse<FsrsUserProfileResponse> updateProfile(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @CurrentUserId
            @Positive(message = "当前用户编号必须为正数") Long ownerUserId,
            @Valid @RequestBody UpdateFsrsUserProfileRequest request
    ) {
        return ApiResponse.success(profileService.update(context(ownerUserId, workspaceId), request));
    }

    @PostMapping("/optimization-jobs")
    public ApiResponse<FsrsOptimizationJobResponse> startOptimization(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @CurrentUserId
            @Positive(message = "当前用户编号必须为正数") Long ownerUserId,
            @RequestBody StartFsrsOptimizationRequest request
    ) {
        return ApiResponse.success(optimizationService.start(context(ownerUserId, workspaceId), request));
    }

    @GetMapping("/profile/versions")
    public ApiResponse<PageResponse<FsrsProfileVersionResponse>> listProfileVersions(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @CurrentUserId
            @Positive(message = "当前用户编号必须为正数") Long ownerUserId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize
    ) {
        return ApiResponse.success(profileService.listVersions(
                context(ownerUserId, workspaceId), page, pageSize
        ));
    }

    @PostMapping("/profile/rollback")
    public ApiResponse<FsrsUserProfileResponse> rollbackProfile(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @CurrentUserId
            @Positive(message = "当前用户编号必须为正数") Long ownerUserId,
            @Valid @RequestBody RollbackFsrsProfileRequest request
    ) {
        return ApiResponse.success(profileService.rollback(context(ownerUserId, workspaceId), request));
    }

    @GetMapping("/optimization-jobs")
    public ApiResponse<PageResponse<FsrsOptimizationJobResponse>> listOptimizationJobs(
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @CurrentUserId
            @Positive(message = "当前用户编号必须为正数") Long ownerUserId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize
    ) {
        return ApiResponse.success(optimizationService.list(
                context(ownerUserId, workspaceId), page, pageSize
        ));
    }

    private AgentToolExecutionContext context(Long ownerUserId, Long workspaceId) {
        return new AgentToolExecutionContext(ownerUserId, workspaceId, null);
    }
}
