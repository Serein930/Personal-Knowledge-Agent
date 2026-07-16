package com.agentmind.user.controller;

import com.agentmind.common.response.ApiResponse;
import com.agentmind.common.security.CurrentUserId;
import com.agentmind.user.model.dto.CurrentUserResponse;
import com.agentmind.user.service.CurrentUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前登录用户接口，本地 JWT 与 OIDC 模式共用。 */
@RestController
@RequestMapping("/api/v1/users/me")
public class CurrentUserController {

    private final CurrentUserService currentUserService;

    public CurrentUserController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ApiResponse<CurrentUserResponse> get(@CurrentUserId Long userId) {
        return ApiResponse.success(currentUserService.get(userId));
    }
}
