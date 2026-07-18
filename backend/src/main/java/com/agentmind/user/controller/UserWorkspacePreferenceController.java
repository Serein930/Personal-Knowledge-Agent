package com.agentmind.user.controller;

import com.agentmind.common.response.ApiResponse;
import com.agentmind.common.security.CurrentUserId;
import com.agentmind.user.model.dto.UpdateUserWorkspacePreferenceRequest;
import com.agentmind.user.model.dto.UserWorkspacePreferenceResponse;
import com.agentmind.user.service.UserWorkspacePreferenceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 设置页使用的用户知识空间偏好接口。 */
@Validated
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/preferences")
public class UserWorkspacePreferenceController {

    private final UserWorkspacePreferenceService preferenceService;

    public UserWorkspacePreferenceController(UserWorkspacePreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public ApiResponse<UserWorkspacePreferenceResponse> get(
            @CurrentUserId Long userId,
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId
    ) {
        return ApiResponse.success(preferenceService.get(userId, workspaceId));
    }

    @PutMapping
    public ApiResponse<UserWorkspacePreferenceResponse> update(
            @CurrentUserId Long userId,
            @PathVariable @Positive(message = "知识空间编号必须为正数") Long workspaceId,
            @Valid @RequestBody UpdateUserWorkspacePreferenceRequest request
    ) {
        return ApiResponse.success(preferenceService.update(userId, workspaceId, request));
    }
}
