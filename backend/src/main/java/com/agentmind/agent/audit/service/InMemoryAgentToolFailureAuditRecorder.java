package com.agentmind.agent.audit.service;

import com.agentmind.agent.audit.model.AgentToolCallAudit;
import com.agentmind.agent.audit.model.AgentToolCallStatus;
import com.agentmind.agent.audit.repository.AgentToolCallAuditRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 内存模式失败审计记录器。
 */
@Component
@ConditionalOnProperty(
        prefix = "agentmind.agent.persistence",
        name = "store",
        havingValue = "memory",
        matchIfMissing = true
)
public class InMemoryAgentToolFailureAuditRecorder implements AgentToolFailureAuditRecorder {

    private final AgentToolCallAuditRepository auditRepository;

    public InMemoryAgentToolFailureAuditRecorder(AgentToolCallAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Override
    public void recordFailure(AgentToolCallAudit audit, long latencyMs, String errorMessage) {
        audit.setStatus(AgentToolCallStatus.FAILED);
        audit.setErrorMessage(errorMessage);
        audit.setLatencyMs(latencyMs);
        auditRepository.save(audit);
    }
}
