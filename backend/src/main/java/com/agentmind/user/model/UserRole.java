package com.agentmind.user.model;

/**
 * 用户角色。
 *
 * <p>该枚举会写入本地 JWT 的角色声明，并作为后续方法级鉴权和后台管理能力的统一角色契约。</p>
 */
public enum UserRole {
    /**
     * 普通个人用户，拥有自己的知识空间和学习资料。
     */
    USER,

    /**
     * 管理员角色，用于后续系统配置、模型配置和平台观测。
     */
    ADMIN
}
