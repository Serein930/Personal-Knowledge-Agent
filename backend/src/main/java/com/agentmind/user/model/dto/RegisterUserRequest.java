package com.agentmind.user.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 本地账号注册请求。 */
public record RegisterUserRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[a-zA-Z0-9._-]{3,64}$", message = "用户名只能包含字母、数字、点、下划线和短横线")
        String username,
        @NotBlank(message = "显示名称不能为空") @Size(max = 100, message = "显示名称不能超过100个字符")
        String displayName,
        @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确")
        String email,
        @NotBlank(message = "密码不能为空")
        @Size(min = 12, max = 72, message = "密码长度必须为12到72个字符")
        String password
) {
}
