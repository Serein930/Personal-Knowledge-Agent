package com.agentmind.user.model.dto;

import java.time.OffsetDateTime;

/** 登录图形验证码挑战；图片使用 data URI，前端无需额外处理二进制响应。 */
public record CaptchaChallengeResponse(
        String challengeId,
        String imageDataUri,
        OffsetDateTime expiresAt
) {
}
