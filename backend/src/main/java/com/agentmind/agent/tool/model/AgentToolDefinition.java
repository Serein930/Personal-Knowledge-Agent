package com.agentmind.agent.tool.model;

import com.agentmind.agent.audit.model.AgentToolType;

/**
 * 智能体工具对模型编排层公开的静态定义。
 *
 * <p>工具名称是稳定的白名单标识，不允许由客户端传入任意 Java 方法名。
 * 后续 Spring AI Tool Calling 适配器会将该定义转换为模型可理解的工具描述。</p>
 */
public record AgentToolDefinition(
        String name,
        String description,
        AgentToolType type,
        String inputSchema
) {

    /**
     * 兼容不需要向模型公开精细参数结构的旧构造方式。
     */
    public AgentToolDefinition(String name, String description, AgentToolType type) {
        this(name, description, type, "{\"type\":\"object\"}");
    }
}
