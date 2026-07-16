package com.agentmind.user.model.dto;

import com.agentmind.user.model.UserRole;
import com.agentmind.user.model.UserStatus;

/** 当前认证用户的安全视图。 */
public record CurrentUserResponse(
        Long id,
        String username,
        String displayName,
        String email,
        UserRole role,
        UserStatus status
) {
}
