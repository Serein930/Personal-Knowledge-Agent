package com.agentmind.user.model;

/**
 * 用户状态。
 *
 * <p>状态字段用于支持账号禁用、风控和后续后台管理。第二阶段不实现登录流程，
 * 但先保留状态契约，避免后续用户模型反复改动。</p>
 */
public enum UserStatus {
    /**
     * 正常可用。
     */
    ACTIVE,

    /**
     * 已禁用，后续鉴权层应拒绝该用户访问业务接口。
     */
    DISABLED
}
