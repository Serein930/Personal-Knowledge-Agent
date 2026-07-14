package com.agentmind.agent.audit.model.dto;

import com.agentmind.agent.audit.model.AgentToolCallStatus;
import com.agentmind.agent.audit.model.AgentToolType;
import java.time.OffsetDateTime;

/**
 * 智能体工具调用摘要响应数据传输对象。
 *
 * <p>检索增强生成问答响应和评估观测页都可以复用该结构展示工具链路。
 * 这里不返回完整请求载荷，避免把潜在敏感数据直接暴露给前端。</p>
 */
public record AgentToolCallSummaryResponse(
        Long id,
        Long conversationId,
        String requestId,
        String toolName,
        AgentToolType toolType,
        AgentToolCallStatus status,
        String requestSummary,
        String responseSummary,
        String errorMessage,
        long latencyMs,
        OffsetDateTime createdAt
) {

    /**
     * 兼容早期 RAG 响应中仅展示工具名称、类型、状态、结果摘要和耗时的构造方式。
     *
     * <p>新增的审计字段默认留空，确保 Stage 2 既有契约和未来完整审计接口可以同时演进。</p>
     */
    public AgentToolCallSummaryResponse(
            String toolName,
            AgentToolType toolType,
            AgentToolCallStatus status,
            String responseSummary,
            long latencyMs
    ) {
        this(null, null, null, toolName, toolType, status, null, responseSummary, null, latencyMs, null);
    }
}
