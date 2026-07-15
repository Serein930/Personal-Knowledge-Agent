package com.agentmind.study.maintenance.model;

/** 后台学习维护使用的用户与知识空间边界。 */
public record StudyDataScope(Long ownerUserId, Long workspaceId) {
}
