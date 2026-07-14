package com.agentmind.agent.audit.service;

import com.agentmind.agent.audit.model.AgentToolCallAudit;

/**
 * 工具调用失败审计记录端口。
 *
 * <p>数据库实现必须使用独立事务，确保业务事务回滚时失败证据仍然保留。</p>
 */
public interface AgentToolFailureAuditRecorder {

    void recordFailure(AgentToolCallAudit audit, long latencyMs, String errorMessage);
}
