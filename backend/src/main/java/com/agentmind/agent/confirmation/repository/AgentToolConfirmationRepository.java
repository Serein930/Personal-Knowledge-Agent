package com.agentmind.agent.confirmation.repository;

import com.agentmind.agent.confirmation.model.AgentToolConfirmation;
import com.agentmind.agent.confirmation.model.AgentToolConfirmationStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 写工具确认单存储端口。
 *
 * <p>状态迁移方法必须由具体适配器保证原子性。数据库实现应使用带原状态条件的更新语句，
 * Redis 实现可使用 Lua 脚本或事务。</p>
 */
public interface AgentToolConfirmationRepository {

    AgentToolConfirmation save(AgentToolConfirmation confirmation);

    Optional<AgentToolConfirmation> findByOwnerUserIdAndWorkspaceIdAndId(
            Long ownerUserId,
            Long workspaceId,
            Long confirmationId
    );

    default Optional<AgentToolConfirmation> compareAndSetStatus(
            Long ownerUserId,
            Long workspaceId,
            Long confirmationId,
            AgentToolConfirmationStatus expectedStatus,
            AgentToolConfirmationStatus targetStatus,
            OffsetDateTime updatedAt
    ) {
        return compareAndSetStatus(
                ownerUserId,
                workspaceId,
                confirmationId,
                expectedStatus,
                targetStatus,
                updatedAt,
                null
        );
    }

    Optional<AgentToolConfirmation> compareAndSetStatus(
            Long ownerUserId,
            Long workspaceId,
            Long confirmationId,
            AgentToolConfirmationStatus expectedStatus,
            AgentToolConfirmationStatus targetStatus,
            OffsetDateTime updatedAt,
            String failureReason
    );

    /**
     * 查找已经超过确认时限、仍处于待确认状态的确认单。
     */
    List<AgentToolConfirmation> findExpiredPendingConfirmations(OffsetDateTime now, int limit);

    /**
     * 查找长时间没有更新、可能因进程中断而遗留的执行中确认单。
     */
    List<AgentToolConfirmation> findStaleExecutingConfirmations(OffsetDateTime updatedBefore, int limit);
}
