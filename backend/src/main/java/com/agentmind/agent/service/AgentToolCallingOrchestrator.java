package com.agentmind.agent.service;

import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.agent.audit.model.dto.AgentToolCallSummaryResponse;
import com.agentmind.agent.model.dto.AgentToolExecutionRequest;
import com.agentmind.agent.model.dto.AgentToolExecutionResponse;
import com.agentmind.common.response.PageResponse;

/**
 * 智能体工具调用编排端口。
 *
 * <p>当前由模拟编排器实现，用于让接口和测试完整覆盖工具边界。
 * 后续 Spring AI Tool Calling 适配器可以实现同一接口，以模型决策替换显式请求决策。</p>
 */
public interface AgentToolCallingOrchestrator {

    AgentToolExecutionResponse execute(
            AgentToolExecutionContext context,
            AgentToolExecutionRequest request
    );

    PageResponse<AgentToolCallSummaryResponse> listAudits(
            AgentToolExecutionContext context,
            int page,
            int pageSize
    );
}
