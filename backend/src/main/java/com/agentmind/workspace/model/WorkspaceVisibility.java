package com.agentmind.workspace.model;

/**
 * 知识空间可见性。
 *
 * <p>个人知识库默认以隐私保护为先，因此当前只开放 PRIVATE。
 * 后续如果支持团队共享或公开知识库，再扩展 TEAM、PUBLIC 等状态。</p>
 */
public enum WorkspaceVisibility {
    /**
     * 私有知识空间，仅拥有者可访问。
     */
    PRIVATE
}
