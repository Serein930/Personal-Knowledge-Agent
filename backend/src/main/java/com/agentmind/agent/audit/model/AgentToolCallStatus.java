package com.agentmind.agent.audit.model;

/**
 * 智能体工具调用状态。
 *
 * <p>该状态用于前端评估观测页展示工具链路，也用于后续定位工具失败原因。</p>
 */
public enum AgentToolCallStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    SKIPPED
}
