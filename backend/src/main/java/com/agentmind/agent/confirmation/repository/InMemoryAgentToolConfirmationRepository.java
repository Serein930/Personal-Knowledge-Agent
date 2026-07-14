package com.agentmind.agent.confirmation.repository;

import com.agentmind.agent.confirmation.model.AgentToolConfirmation;
import com.agentmind.agent.confirmation.model.AgentToolConfirmationStatus;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * 写工具确认单的内存适配器。
 *
 * <p>该实现用于当前本地联调阶段，服务重启后确认单会清空。状态迁移使用并发映射表的原子计算，
 * 保证两个确认请求不能同时把同一张确认单推进到执行中。</p>
 */
@Repository
@ConditionalOnProperty(
        prefix = "agentmind.agent.persistence",
        name = "store",
        havingValue = "memory",
        matchIfMissing = true
)
public class InMemoryAgentToolConfirmationRepository implements AgentToolConfirmationRepository {

    private final AtomicLong idGenerator = new AtomicLong(1);
    private final ConcurrentHashMap<Long, AgentToolConfirmation> confirmations = new ConcurrentHashMap<>();

    @Override
    public AgentToolConfirmation save(AgentToolConfirmation confirmation) {
        AgentToolConfirmation stored = confirmation.id() == null
                ? confirmation.withId(idGenerator.getAndIncrement())
                : confirmation;
        confirmations.put(stored.id(), stored);
        return stored;
    }

    @Override
    public Optional<AgentToolConfirmation> findByOwnerUserIdAndWorkspaceIdAndId(
            Long ownerUserId,
            Long workspaceId,
            Long confirmationId
    ) {
        return Optional.ofNullable(confirmations.get(confirmationId))
                .filter(confirmation -> ownerUserId.equals(confirmation.ownerUserId()))
                .filter(confirmation -> workspaceId.equals(confirmation.workspaceId()));
    }

    @Override
    public Optional<AgentToolConfirmation> compareAndSetStatus(
            Long ownerUserId,
            Long workspaceId,
            Long confirmationId,
            AgentToolConfirmationStatus expectedStatus,
            AgentToolConfirmationStatus targetStatus,
            OffsetDateTime updatedAt,
            String failureReason
    ) {
        AtomicReference<AgentToolConfirmation> transitioned = new AtomicReference<>();
        confirmations.computeIfPresent(confirmationId, (ignored, current) -> {
            if (!ownerUserId.equals(current.ownerUserId())
                    || !workspaceId.equals(current.workspaceId())
                    || current.status() != expectedStatus) {
                return current;
            }
            AgentToolConfirmation next = current.transitionTo(targetStatus, failureReason, updatedAt);
            transitioned.set(next);
            return next;
        });
        return Optional.ofNullable(transitioned.get());
    }

    @Override
    public List<AgentToolConfirmation> findExpiredPendingConfirmations(OffsetDateTime now, int limit) {
        return confirmations.values().stream()
                .filter(item -> item.status() == AgentToolConfirmationStatus.PENDING_CONFIRMATION)
                .filter(item -> !item.expiresAt().isAfter(now))
                .sorted(Comparator.comparing(AgentToolConfirmation::expiresAt))
                .limit(Math.max(0, limit))
                .toList();
    }

    @Override
    public List<AgentToolConfirmation> findStaleExecutingConfirmations(
            OffsetDateTime updatedBefore,
            int limit
    ) {
        return confirmations.values().stream()
                .filter(item -> item.status() == AgentToolConfirmationStatus.EXECUTING)
                .filter(item -> !item.updatedAt().isAfter(updatedBefore))
                .sorted(Comparator.comparing(AgentToolConfirmation::updatedAt))
                .limit(Math.max(0, limit))
                .toList();
    }
}
