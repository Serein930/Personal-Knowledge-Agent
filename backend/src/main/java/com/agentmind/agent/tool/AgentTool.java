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

    /**
     * 在执行工具前校验模型或前端提交的参数。
     *
     * <p>写工具确认单必须在生成确认令牌前完成参数校验，因此校验能力需要与真正执行动作分离。
     * 默认实现保留对既有工具的兼容性，新工具应覆盖该方法并给出明确的字段约束。</p>
     */
    default void validateArguments(JsonNode arguments) {
        AgentToolArgumentReader.requireObject(arguments);
    }

    AgentToolExecutionResult execute(AgentToolExecutionContext context, JsonNode arguments);
}
