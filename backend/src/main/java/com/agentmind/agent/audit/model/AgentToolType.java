package com.agentmind.agent.audit.model;

/**
 * Agent 工具类型。
 *
 * <p>工具类型用于区分只读检索、写入业务数据和分析型工具。后续权限系统可以基于该类型
 * 对写操作工具做更严格的确认和审计。</p>
 */
public enum AgentToolType {
    READ,
    WRITE,
    ANALYSIS
}
