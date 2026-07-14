package com.agentmind.agent.audit.service;

import com.agentmind.agent.audit.model.AgentToolCallAudit;
import com.agentmind.agent.audit.model.AgentToolCallStatus;
import com.agentmind.agent.audit.repository.AgentToolCallAuditRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 使用独立数据库事务保存失败审计。
 *
 * <p>写工具执行失败时，确认单状态迁移和业务数据会随外层事务回滚。本记录器挂起外层事务，
 * 使用新连接插入一条最终 {@code FAILED} 审计，从而避免失败记录被一起回滚。</p>
 */
@Component
@ConditionalOnProperty(prefix = "agentmind.agent.persistence", name = "store", havingValue = "jdbc")
public class JdbcAgentToolFailureAuditRecorder implements AgentToolFailureAuditRecorder {

    private final AgentToolCallAuditRepository auditRepository;
    private final TransactionTemplate requiresNewTransaction;

    public JdbcAgentToolFailureAuditRecorder(
            AgentToolCallAuditRepository auditRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.auditRepository = auditRepository;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void recordFailure(AgentToolCallAudit audit, long latencyMs, String errorMessage) {
        requiresNewTransaction.executeWithoutResult(status -> auditRepository.save(
                copyAsFailure(audit, latencyMs, errorMessage)
        ));
    }

    private AgentToolCallAudit copyAsFailure(
            AgentToolCallAudit source,
            long latencyMs,
            String errorMessage
    ) {
        AgentToolCallAudit failure = new AgentToolCallAudit();
        failure.setOwnerUserId(source.getOwnerUserId());
        failure.setWorkspaceId(source.getWorkspaceId());
        failure.setConversationId(source.getConversationId());
        failure.setMessageId(source.getMessageId());
        failure.setRequestId(source.getRequestId());
        failure.setToolName(source.getToolName());
        failure.setToolType(source.getToolType());
        failure.setRequestPayload(source.getRequestPayload());
        failure.setRequestFingerprint(source.getRequestFingerprint());
        failure.setStatus(AgentToolCallStatus.FAILED);
        failure.setErrorMessage(errorMessage);
        failure.setLatencyMs(latencyMs);
        failure.setCreatedAt(source.getCreatedAt());
        return failure;
    }
}
