package com.agentmind.user.controller;

import com.agentmind.common.response.ApiResponse;
import com.agentmind.user.model.dto.AuthTokenResponse;
import com.agentmind.user.model.dto.LoginRequest;
import com.agentmind.user.model.dto.RegisterUserRequest;
import com.agentmind.user.service.LocalAuthenticationService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 本地 JWT 模式的注册和登录接口。 */
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "agentmind.security", name = "mode", havingValue = "local-jwt")
public class LocalAuthenticationController {

    private final LocalAuthenticationService authenticationService;

    public LocalAuthenticationController(LocalAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthTokenResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        return ApiResponse.success(authenticationService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authenticationService.login(request));
    }
}
