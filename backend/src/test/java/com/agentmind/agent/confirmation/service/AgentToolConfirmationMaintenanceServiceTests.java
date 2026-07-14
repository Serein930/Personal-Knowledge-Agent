package com.agentmind.agent.confirmation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.agent.confirmation.config.AgentToolConfirmationMaintenanceProperties;
import com.agentmind.agent.confirmation.model.AgentToolConfirmation;
import com.agentmind.agent.confirmation.model.AgentToolConfirmationStatus;
import com.agentmind.agent.confirmation.repository.InMemoryAgentToolConfirmationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/**
 * 确认单过期与异常执行恢复规则测试。
 */
class AgentToolConfirmationMaintenanceServiceTests {

    @Test
    void maintenanceShouldExpirePendingAndFailStaleExecutingWithoutTouchingFreshRecord() {
        InMemoryAgentToolConfirmationRepository repository = new InMemoryAgentToolConfirmationRepository();
        AgentToolConfirmationMaintenanceProperties properties = new AgentToolConfirmationMaintenanceProperties();
        properties.setExecutingTimeout(Duration.ofMinutes(5));
        properties.setBatchSize(20);
        AgentToolConfirmationMaintenanceService service =
                new AgentToolConfirmationMaintenanceService(repository, properties);
        OffsetDateTime now = OffsetDateTime.now();

        AgentToolConfirmation expired = repository.save(confirmation(
                AgentToolConfirmationStatus.PENDING_CONFIRMATION,
                now.minusMinutes(10),
                now.minusMinutes(1)
        ));
        AgentToolConfirmation staleExecuting = repository.save(confirmation(
                AgentToolConfirmationStatus.EXECUTING,
                now.minusMinutes(10),
                now.plusMinutes(10)
        ));
        AgentToolConfirmation fresh = repository.save(confirmation(
                AgentToolConfirmationStatus.PENDING_CONFIRMATION,
                now,
                now.plusMinutes(5)
        ));

        AgentToolConfirmationMaintenanceResult result = service.maintain();

        assertThat(result.expiredCount()).isEqualTo(1);
        assertThat(result.recoveredCount()).isEqualTo(1);
        assertThat(find(repository, expired).status()).isEqualTo(AgentToolConfirmationStatus.EXPIRED);
        AgentToolConfirmation recovered = find(repository, staleExecuting);
        assertThat(recovered.status()).isEqualTo(AgentToolConfirmationStatus.FAILED);
        assertThat(recovered.failureReason())
                .isEqualTo(AgentToolConfirmationMaintenanceService.STALE_EXECUTION_REASON);
        assertThat(find(repository, fresh).status())
                .isEqualTo(AgentToolConfirmationStatus.PENDING_CONFIRMATION);
    }

    private AgentToolConfirmation find(
            InMemoryAgentToolConfirmationRepository repository,
            AgentToolConfirmation confirmation
    ) {
        return repository.findByOwnerUserIdAndWorkspaceIdAndId(
                confirmation.ownerUserId(),
                confirmation.workspaceId(),
                confirmation.id()
        ).orElseThrow();
    }

    private AgentToolConfirmation confirmation(
            AgentToolConfirmationStatus status,
            OffsetDateTime updatedAt,
            OffsetDateTime expiresAt
    ) {
        return new AgentToolConfirmation(
                null,
                1L,
                9L,
                11L,
                22L,
                "maintenance-" + status + "-" + updatedAt,
                CreateTestToolNames.WRITE_TOOL,
                new ObjectMapper().createObjectNode().put("title", "测试"),
                "测试参数",
                "token-digest",
                status,
                null,
                null,
                updatedAt.minusMinutes(1),
                expiresAt,
                updatedAt,
                null
        );
    }

    /**
     * 测试只关心确认单状态，不需要注册实际工具。
     */
    private static final class CreateTestToolNames {
        private static final String WRITE_TOOL = "test.write";
    }
}
