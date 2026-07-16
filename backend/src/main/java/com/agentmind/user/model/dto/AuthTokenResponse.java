package com.agentmind.user.model.dto;

import java.time.OffsetDateTime;

/** 登录成功后的短期访问令牌。 */
public record AuthTokenResponse(
        String tokenType,
        String accessToken,
        OffsetDateTime expiresAt,
        Long userId,
        Long defaultWorkspaceId
) {
}
