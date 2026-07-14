package com.agentmind.agent.audit.repository;

import com.agentmind.agent.audit.model.AgentToolCallAudit;
import java.util.List;
import java.util.Optional;

/**
 * 智能体工具调用审计存储端口。
 *
 * <p>当前仅提供内存实现，后续可增加数据库实现而不影响工具编排服务和接口契约。</p>
 */
public interface AgentToolCallAuditRepository {

    AgentToolCallAudit save(AgentToolCallAudit audit);

    Optional<AgentToolCallAudit> findSucceededByExecutionKey(
            Long ownerUserId,
            Long workspaceId,
            String requestId
    );

    List<AgentToolCallAudit> findByOwnerUserIdAndWorkspaceId(Long ownerUserId, Long workspaceId);
}
