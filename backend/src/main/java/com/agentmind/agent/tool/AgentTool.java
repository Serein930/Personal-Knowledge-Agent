package com.agentmind.agent.tool;

import com.agentmind.agent.tool.model.AgentToolDefinition;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.agent.tool.model.AgentToolExecutionResult;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 智能体工具统一执行端口。
 *
 * <p>每个工具只负责本身的参数解释和业务动作。白名单校验、上下文校验、审计和幂等处理由编排服务统一承担。</p>
 */
public interface AgentTool {

    AgentToolDefinition definition();

    AgentToolExecutionResult execute(AgentToolExecutionContext context, JsonNode arguments);
}
