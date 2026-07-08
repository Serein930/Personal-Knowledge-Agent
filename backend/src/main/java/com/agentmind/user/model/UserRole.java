package com.agentmind.user.model;

/**
 * 用户角色。
 *
 * <p>Stage 2 只定义角色边界，不实现 Spring Security 权限校验。
 * 后续接入认证授权时，该枚举会用于 JWT Claims、接口鉴权和后台管理能力。</p>
 */
public enum UserRole {
    /**
     * 普通个人用户，拥有自己的知识空间和学习资料。
     */
    USER,

    /**
     * 管理员角色，后续用于系统配置、模型配置和平台观测。
     */
    ADMIN
}
