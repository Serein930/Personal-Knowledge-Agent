package com.agentmind.user.model.dto;

import jakarta.validation.constraints.NotBlank;

/** 本地账号登录请求。 */
public record LoginRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @NotBlank(message = "密码不能为空") String password,
        @NotBlank(message = "验证码挑战编号不能为空") String captchaChallengeId,
        @NotBlank(message = "验证码不能为空") String captchaCode
) {
    /** 保留给不启用验证码的服务层单元测试使用。 */
    public LoginRequest(String username, String password) {
        this(username, password, null, null);
    }
}
