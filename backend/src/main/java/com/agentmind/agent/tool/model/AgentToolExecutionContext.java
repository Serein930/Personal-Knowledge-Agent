package com.agentmind.agent.tool.model;

/**
 * 单次智能体工具执行的业务上下文。
 *
 * <p>该对象只保存可信的归属信息，不保存由模型自由生成的内容。
 * 当前演示阶段的用户编号由控制层请求头提供；接入 Spring Security 后将改由认证主体构造。</p>
 */
public record AgentToolExecutionContext(
        Long ownerUserId,
        Long workspaceId,
        Long conversationId,
        Long messageId
) {

    /**
     * 兼容显式工具调用接口原有的三字段上下文。
     */
    public AgentToolExecutionContext(Long ownerUserId, Long workspaceId, Long conversationId) {
        this(ownerUserId, workspaceId, conversationId, null);
    }
}
