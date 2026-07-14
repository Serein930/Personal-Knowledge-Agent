package com.agentmind.agent.confirmation.service;

import com.agentmind.agent.audit.model.AgentToolType;
import com.agentmind.agent.confirmation.config.AgentWriteToolConfirmationProperties;
import com.agentmind.agent.confirmation.model.AgentToolConfirmation;
import com.agentmind.agent.confirmation.model.AgentToolConfirmationStatus;
import com.agentmind.agent.confirmation.model.dto.AgentToolConfirmationResponse;
import com.agentmind.agent.confirmation.model.dto.CreateAgentToolConfirmationRequest;
import com.agentmind.agent.confirmation.model.dto.CreatedAgentToolConfirmationResponse;
import com.agentmind.agent.confirmation.model.dto.DecidedAgentToolConfirmationResponse;
import com.agentmind.agent.confirmation.repository.AgentToolConfirmationRepository;
import com.agentmind.agent.model.dto.AgentToolExecutionRequest;
import com.agentmind.agent.model.dto.AgentToolExecutionResponse;
import com.agentmind.agent.service.AgentToolCallingOrchestrator;
import com.agentmind.agent.service.AgentToolExecutionAuthorizer;
import com.agentmind.agent.tool.AgentTool;
import com.agentmind.agent.tool.AgentToolRegistry;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 写工具确认应用服务。
 *
 * <p>该服务负责确认单状态机、令牌校验、权限复核、幂等键传递和事务边界编排。
 * 业务工具本身不接触确认令牌，避免安全规则散落在每个写工具中。</p>
 */
@Service
public class AgentToolConfirmationApplicationService {

    private final AgentToolRegistry toolRegistry;
    private final AgentToolExecutionAuthorizer authorizer;
    private final AgentToolConfirmationRepository confirmationRepository;
    private final AgentToolConfirmationTokenService tokenService;
    private final AgentWriteToolTransactionBoundary transactionBoundary;
    private final AgentToolCallingOrchestrator toolCallingOrchestrator;
    private final AgentWriteToolConfirmationProperties properties;

    public AgentToolConfirmationApplicationService(
            AgentToolRegistry toolRegistry,
            AgentToolExecutionAuthorizer authorizer,
            AgentToolConfirmationRepository confirmationRepository,
            AgentToolConfirmationTokenService tokenService,
            AgentWriteToolTransactionBoundary transactionBoundary,
            AgentToolCallingOrchestrator toolCallingOrchestrator,
            AgentWriteToolConfirmationProperties properties
    ) {
        this.toolRegistry = toolRegistry;
        this.authorizer = authorizer;
        this.confirmationRepository = confirmationRepository;
        this.tokenService = tokenService;
        this.transactionBoundary = transactionBoundary;
        this.toolCallingOrchestrator = toolCallingOrchestrator;
        this.properties = properties;
    }

    public CreatedAgentToolConfirmationResponse create(
            AgentToolExecutionContext context,
            CreateAgentToolConfirmationRequest request
    ) {
        authorizer.authorize(context);
        AgentTool tool = toolRegistry.requireTool(request.toolName());
        if (tool.definition().type() != AgentToolType.WRITE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只有写工具需要创建确认单");
        }
        tool.validateArguments(request.arguments());

        AgentToolConfirmationTokenService.IssuedConfirmationToken issuedToken = tokenService.issue();
        OffsetDateTime now = OffsetDateTime.now();
        AgentToolConfirmation confirmation = confirmationRepository.save(new AgentToolConfirmation(
                null,
                context.ownerUserId(),
                context.workspaceId(),
                context.conversationId(),
                context.messageId(),
                request.requestId().trim(),
                tool.definition().name(),
                request.arguments().deepCopy(),
                summarizeArguments(request.arguments()),
                issuedToken.digest(),
                AgentToolConfirmationStatus.PENDING_CONFIRMATION,
                null,
                null,
                now,
                now.plus(properties.getTtl()),
                now,
                null
        ));
        return new CreatedAgentToolConfirmationResponse(toResponse(confirmation), issuedToken.rawToken());
    }

    public AgentToolConfirmationResponse get(
            Long ownerUserId,
            Long workspaceId,
            Long confirmationId
    ) {
        AgentToolConfirmation confirmation = requireConfirmation(ownerUserId, workspaceId, confirmationId);
        authorizer.authorize(toContext(confirmation));
        return toResponse(confirmation);
    }

    public DecidedAgentToolConfirmationResponse confirm(
            Long ownerUserId,
            Long workspaceId,
            Long confirmationId,
            String rawToken
    ) {
        AgentToolConfirmation confirmation = requireConfirmation(ownerUserId, workspaceId, confirmationId);
        authorizer.authorize(toContext(confirmation));
        verifyToken(confirmation, rawToken);
        expireIfNecessary(confirmation);

        if (confirmation.status() == AgentToolConfirmationStatus.SUCCEEDED) {
            return new DecidedAgentToolConfirmationResponse(toResponse(confirmation), true);
        }
        requirePending(confirmation);

        return transactionBoundary.execute(() -> executeConfirmedWrite(
                ownerUserId, workspaceId, confirmationId
        ));
    }

    public DecidedAgentToolConfirmationResponse reject(
            Long ownerUserId,
            Long workspaceId,
            Long confirmationId,
            String rawToken
    ) {
        AgentToolConfirmation confirmation = requireConfirmation(ownerUserId, workspaceId, confirmationId);
        authorizer.authorize(toContext(confirmation));
        verifyToken(confirmation, rawToken);
        expireIfNecessary(confirmation);
        if (confirmation.status() == AgentToolConfirmationStatus.REJECTED) {
            return new DecidedAgentToolConfirmationResponse(toResponse(confirmation), true);
        }
        requirePending(confirmation);

        return transactionBoundary.execute(() -> {
            AgentToolConfirmation rejected = confirmationRepository.compareAndSetStatus(
                            ownerUserId,
                            workspaceId,
                            confirmationId,
                            AgentToolConfirmationStatus.PENDING_CONFIRMATION,
                            AgentToolConfirmationStatus.REJECTED,
                            OffsetDateTime.now()
                    )
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESOURCE_CONFLICT,
                            "确认单状态已经变化，请刷新后重试"
                    ));
            return new DecidedAgentToolConfirmationResponse(toResponse(rejected), false);
        });
    }

    private DecidedAgentToolConfirmationResponse executeConfirmedWrite(
            Long ownerUserId,
            Long workspaceId,
            Long confirmationId
    ) {
        AgentToolConfirmation executing = confirmationRepository.compareAndSetStatus(
                        ownerUserId,
                        workspaceId,
                        confirmationId,
                        AgentToolConfirmationStatus.PENDING_CONFIRMATION,
                        AgentToolConfirmationStatus.EXECUTING,
                        OffsetDateTime.now()
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_CONFLICT,
                        "确认单正在执行或已经处理"
                ));
        try {
            AgentToolExecutionResponse execution = toolCallingOrchestrator.executeConfirmedWrite(
                    toContext(executing),
                    new AgentToolExecutionRequest(
                            executing.conversationId(),
                            executing.toolName(),
                            executing.requestId(),
                            executing.arguments()
                    )
            );
            AgentToolConfirmation succeeded = confirmationRepository.save(
                    executing.succeed(execution, OffsetDateTime.now())
            );
            return new DecidedAgentToolConfirmationResponse(toResponse(succeeded), execution.reused());
        } catch (RuntimeException exception) {
            confirmationRepository.save(executing.fail(safeFailureReason(exception), OffsetDateTime.now()));
            throw exception;
        }
    }

    private AgentToolConfirmation requireConfirmation(Long ownerUserId, Long workspaceId, Long confirmationId) {
        return confirmationRepository.findByOwnerUserIdAndWorkspaceIdAndId(
                        ownerUserId, workspaceId, confirmationId
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "写工具确认单不存在或无权访问"
                ));
    }

    private void verifyToken(AgentToolConfirmation confirmation, String rawToken) {
        if (!tokenService.matches(rawToken, confirmation.tokenDigest())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "写工具确认令牌不正确");
        }
    }

    private void expireIfNecessary(AgentToolConfirmation confirmation) {
        OffsetDateTime now = OffsetDateTime.now();
        if (confirmation.status() == AgentToolConfirmationStatus.PENDING_CONFIRMATION
                && !now.isBefore(confirmation.expiresAt())) {
            confirmationRepository.compareAndSetStatus(
                    confirmation.ownerUserId(),
                    confirmation.workspaceId(),
                    confirmation.id(),
                    AgentToolConfirmationStatus.PENDING_CONFIRMATION,
                    AgentToolConfirmationStatus.EXPIRED,
                    now
            );
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "写工具确认单已过期");
        }
    }

    private void requirePending(AgentToolConfirmation confirmation) {
        if (confirmation.status() != AgentToolConfirmationStatus.PENDING_CONFIRMATION) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_CONFLICT,
                    "当前确认单状态不允许执行该操作：" + confirmation.status()
            );
        }
    }

    private AgentToolExecutionContext toContext(AgentToolConfirmation confirmation) {
        return new AgentToolExecutionContext(
                confirmation.ownerUserId(),
                confirmation.workspaceId(),
                confirmation.conversationId(),
                confirmation.messageId()
        );
    }

    private AgentToolConfirmationResponse toResponse(AgentToolConfirmation confirmation) {
        return new AgentToolConfirmationResponse(
                confirmation.id(),
                confirmation.workspaceId(),
                confirmation.conversationId(),
                confirmation.messageId(),
                confirmation.requestId(),
                confirmation.toolName(),
                confirmation.argumentSummary(),
                confirmation.status(),
                confirmation.executionResponse(),
                confirmation.failureReason(),
                confirmation.createdAt(),
                confirmation.expiresAt(),
                confirmation.updatedAt(),
                confirmation.executedAt()
        );
    }

    private String summarizeArguments(JsonNode arguments) {
        List<String> fieldNames = new ArrayList<>();
        arguments.fieldNames().forEachRemaining(fieldNames::add);
        return "即将写入，参数字段：" + String.join("、", fieldNames);
    }

    private String safeFailureReason(RuntimeException exception) {
        return exception instanceof BusinessException ? exception.getMessage() : "写工具执行发生未预期异常";
    }
}
