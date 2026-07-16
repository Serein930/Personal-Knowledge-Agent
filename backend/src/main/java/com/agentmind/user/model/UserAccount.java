package com.agentmind.user.model;

import java.time.OffsetDateTime;

/** 持久化用户账号快照，不向控制层暴露密码摘要。 */
public record UserAccount(
        Long id,
        String username,
        String displayName,
        String email,
        String passwordHash,
        UserRole role,
        UserStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
