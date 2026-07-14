package com.agentmind.agent.confirmation.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 周期触发确认单维护的轻量调度入口。
 *
 * <p>业务规则全部位于维护服务中，调度器只负责触发，便于单元测试直接调用服务并保持结果可重复。</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "agentmind.agent.confirmation-maintenance",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AgentToolConfirmationMaintenanceScheduler {

    private final AgentToolConfirmationMaintenanceService maintenanceService;

    public AgentToolConfirmationMaintenanceScheduler(
            AgentToolConfirmationMaintenanceService maintenanceService
    ) {
        this.maintenanceService = maintenanceService;
    }

    @Scheduled(
            fixedDelayString = "${agentmind.agent.confirmation-maintenance.fixed-delay-millis:60000}",
            initialDelayString = "${agentmind.agent.confirmation-maintenance.initial-delay-millis:60000}"
    )
    public void maintainConfirmations() {
        maintenanceService.maintain();
    }
}
