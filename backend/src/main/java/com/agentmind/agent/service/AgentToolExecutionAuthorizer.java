package com.agentmind.agent.service;

import com.agentmind.agent.tool.model.AgentToolExecutionContext;

/**
 * 智能体工具执行上下文的权限校验端口。
 *
 * <p>后续接入 Spring Security 后，该端口将基于认证主体和知识空间成员关系校验。
 * 业务工具无需感知具体认证框架。</p>
 */
public interface AgentToolExecutionAuthorizer {

    void authorize(AgentToolExecutionContext context);
}
