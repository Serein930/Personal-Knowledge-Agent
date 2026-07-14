package com.agentmind.agent.confirmation.model;

/**
 * 写工具确认单状态。
 *
 * <p>只有待确认状态可以原子迁移到执行中或已拒绝，防止同一确认单被并发重复执行。</p>
 */
public enum AgentToolConfirmationStatus {
    PENDING_CONFIRMATION,
    EXECUTING,
    SUCCEEDED,
    REJECTED,
    EXPIRED,
    FAILED
}
