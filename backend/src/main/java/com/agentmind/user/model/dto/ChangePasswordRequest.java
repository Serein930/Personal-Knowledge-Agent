package com.agentmind.user.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 用户通过当前凭据修改本地账号密码。 */
public record ChangePasswordRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[a-zA-Z0-9._-]{3,64}$", message = "用户名格式不正确")
        String username,
        @NotBlank(message = "当前密码不能为空") String currentPassword,
        @NotBlank(message = "新密码不能为空")
        @Size(min = 12, max = 72, message = "新密码长度必须为12到72个字符")
        String newPassword,
        @NotBlank(message = "验证码挑战编号不能为空") String captchaChallengeId,
        @NotBlank(message = "验证码不能为空") String captchaCode
) {
}
