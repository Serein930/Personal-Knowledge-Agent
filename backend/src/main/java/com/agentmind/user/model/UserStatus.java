package com.agentmind.user.model;

/**
 * 用户状态。
 *
 * <p>状态字段用于支持账号禁用、风控和后台管理。本地认证服务在签发令牌前会检查该状态。</p>
 */
public enum UserStatus {
    /**
     * 正常可用。
     */
    ACTIVE,

    /**
     * 已禁用，认证服务应拒绝为该用户签发访问令牌。
     */
    DISABLED
}
