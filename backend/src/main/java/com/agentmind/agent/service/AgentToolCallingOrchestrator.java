package com.agentmind.agent.service;

import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.agent.audit.model.dto.AgentToolCallSummaryResponse;
import com.agentmind.agent.model.dto.AgentToolExecutionRequest;
import com.agentmind.agent.model.dto.AgentToolExecutionResponse;
import com.agentmind.common.response.PageResponse;
import java.util.List;

/**
 * 智能体工具调用编排端口。
 *
 * <p>显式 REST 调用和 Spring AI 模型调用均依赖该端口，确保两条入口共享相同的权限、审计与幂等语义。</p>
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

    /**
     * 查询一次回答消息触发的全部工具调用摘要。
     */
    List<AgentToolCallSummaryResponse> findAuditsForExecution(AgentToolExecutionContext context);
}
