package com.agentmind.agent.confirmation.service;

import com.agentmind.agent.confirmation.config.AgentToolConfirmationMaintenanceProperties;
import com.agentmind.agent.confirmation.model.AgentToolConfirmation;
import com.agentmind.agent.confirmation.model.AgentToolConfirmationStatus;
import com.agentmind.agent.confirmation.repository.AgentToolConfirmationRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 写工具确认单后台维护服务。
 *
 * <p>待确认单超过有效期后原子迁移为过期；长时间停留在执行中的确认单说明执行进程可能中断，
 * 系统将其原子迁移为失败并保留原因。恢复任务绝不重放写工具，避免在结果不确定时产生重复写入。</p>
 */
@Service
public class AgentToolConfirmationMaintenanceService {

    public static final String STALE_EXECUTION_REASON = "执行进程中断或超时，系统未自动重放写工具，请重新发起确认";

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentToolConfirmationMaintenanceService.class);

    private final AgentToolConfirmationRepository confirmationRepository;
    private final AgentToolConfirmationMaintenanceProperties properties;

    public AgentToolConfirmationMaintenanceService(
            AgentToolConfirmationRepository confirmationRepository,
            AgentToolConfirmationMaintenanceProperties properties
    ) {
        this.confirmationRepository = confirmationRepository;
        this.properties = properties;
    }

    public AgentToolConfirmationMaintenanceResult maintain() {
        OffsetDateTime now = OffsetDateTime.now();
        int batchSize = Math.max(1, properties.getBatchSize());
        int expiredCount = expirePendingConfirmations(now, batchSize);
        int recoveredCount = recoverStaleExecutions(
                now,
                now.minus(properties.getExecutingTimeout()),
                batchSize
        );
        if (expiredCount > 0 || recoveredCount > 0) {
            LOGGER.info("写工具确认单维护完成：过期数量={}，执行中恢复数量={}", expiredCount, recoveredCount);
        }
        return new AgentToolConfirmationMaintenanceResult(expiredCount, recoveredCount);
    }

    private int expirePendingConfirmations(OffsetDateTime now, int batchSize) {
        List<AgentToolConfirmation> expired = confirmationRepository.findExpiredPendingConfirmations(now, batchSize);
        int transitioned = 0;
        for (AgentToolConfirmation confirmation : expired) {
            if (confirmationRepository.compareAndSetStatus(
                    confirmation.ownerUserId(),
                    confirmation.workspaceId(),
                    confirmation.id(),
                    AgentToolConfirmationStatus.PENDING_CONFIRMATION,
                    AgentToolConfirmationStatus.EXPIRED,
                    now
            ).isPresent()) {
                transitioned++;
            }
        }
        return transitioned;
    }

    private int recoverStaleExecutions(
            OffsetDateTime now,
            OffsetDateTime updatedBefore,
            int batchSize
    ) {
        List<AgentToolConfirmation> stale = confirmationRepository.findStaleExecutingConfirmations(
                updatedBefore,
                batchSize
        );
        int transitioned = 0;
        for (AgentToolConfirmation confirmation : stale) {
            if (confirmationRepository.compareAndSetStatus(
                    confirmation.ownerUserId(),
                    confirmation.workspaceId(),
                    confirmation.id(),
                    AgentToolConfirmationStatus.EXECUTING,
                    AgentToolConfirmationStatus.FAILED,
                    now,
                    STALE_EXECUTION_REASON
            ).isPresent()) {
                transitioned++;
            }
        }
        return transitioned;
    }
}
