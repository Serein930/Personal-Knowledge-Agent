package com.agentmind.agent.audit.repository;

import com.agentmind.agent.audit.model.AgentToolCallAudit;
import com.agentmind.agent.audit.model.AgentToolCallStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 开发阶段使用的工具审计内存适配器。
 *
 * <p>审计编号由本地递增序列生成，查询结果按创建时间倒序返回。生产阶段替换为数据库实现时，
 * 必须保留“用户、知识空间、请求编号”联合查重语义。</p>
 */
@Primary
@Repository
public class InMemoryAgentToolCallAuditRepository implements AgentToolCallAuditRepository {

    private final AtomicLong idGenerator = new AtomicLong(1);
    private final ConcurrentHashMap<Long, AgentToolCallAudit> audits = new ConcurrentHashMap<>();

    @Override
    public AgentToolCallAudit save(AgentToolCallAudit audit) {
        if (audit.getId() == null) {
            audit.setId(idGenerator.getAndIncrement());
        }
        audits.put(audit.getId(), audit);
        return audit;
    }

    @Override
    public Optional<AgentToolCallAudit> findSucceededByExecutionKey(
            Long ownerUserId,
            Long workspaceId,
            String requestId
    ) {
        if (!StringUtils.hasText(requestId)) {
            return Optional.empty();
        }
        return audits.values().stream()
                .filter(audit -> ownerUserId.equals(audit.getOwnerUserId()))
                .filter(audit -> workspaceId.equals(audit.getWorkspaceId()))
                .filter(audit -> requestId.equals(audit.getRequestId()))
                .filter(audit -> audit.getStatus() == AgentToolCallStatus.SUCCEEDED)
                .findFirst();
    }

    @Override
    public List<AgentToolCallAudit> findByOwnerUserIdAndWorkspaceId(Long ownerUserId, Long workspaceId) {
        return audits.values().stream()
                .filter(audit -> ownerUserId.equals(audit.getOwnerUserId()))
                .filter(audit -> workspaceId.equals(audit.getWorkspaceId()))
                .sorted(Comparator.comparing(AgentToolCallAudit::getCreatedAt).reversed())
                .toList();
    }
}
