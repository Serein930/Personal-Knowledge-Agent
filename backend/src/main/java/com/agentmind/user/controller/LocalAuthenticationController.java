package com.agentmind.user.controller;

import com.agentmind.common.response.ApiResponse;
import com.agentmind.common.security.CurrentUserId;
import com.agentmind.user.model.dto.AuthTokenResponse;
import com.agentmind.user.model.dto.CaptchaChallengeResponse;
import com.agentmind.user.model.dto.ChangePasswordRequest;
import com.agentmind.user.model.dto.LoginRequest;
import com.agentmind.user.model.dto.RegisterUserRequest;
import com.agentmind.user.service.LocalAuthenticationService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 本地 JWT 模式的注册和登录接口。 */
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "agentmind.security", name = "mode", havingValue = "local-jwt")
public class LocalAuthenticationController {

    private final LocalAuthenticationService authenticationService;
    private final com.agentmind.user.service.LoginCaptchaService captchaService;

    public LocalAuthenticationController(
            LocalAuthenticationService authenticationService,
            com.agentmind.user.service.LoginCaptchaService captchaService
    ) {
        this.authenticationService = authenticationService;
        this.captchaService = captchaService;
    }

    @GetMapping("/captcha")
    public ApiResponse<CaptchaChallengeResponse> captcha() {
        return ApiResponse.success(captchaService.createChallenge());
    }

    @PostMapping("/register")
    public ApiResponse<AuthTokenResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        return ApiResponse.success(authenticationService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authenticationService.login(request));
    }

    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authenticationService.changePassword(request);
        return ApiResponse.success("密码修改成功，请使用新密码重新登录", null);
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokenResponse> refresh(@CurrentUserId Long userId) {
        return ApiResponse.success(authenticationService.refresh(userId));
    }
}
