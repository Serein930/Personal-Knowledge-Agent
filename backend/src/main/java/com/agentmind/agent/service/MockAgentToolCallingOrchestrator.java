package com.agentmind.agent.service;

import com.agentmind.agent.audit.model.AgentToolCallAudit;
import com.agentmind.agent.audit.model.AgentToolCallStatus;
import com.agentmind.agent.audit.model.dto.AgentToolCallSummaryResponse;
import com.agentmind.agent.audit.repository.AgentToolCallAuditRepository;
import com.agentmind.agent.model.dto.AgentToolExecutionRequest;
import com.agentmind.agent.model.dto.AgentToolExecutionResponse;
import com.agentmind.agent.tool.AgentTool;
import com.agentmind.agent.tool.AgentToolRegistry;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.agent.tool.model.AgentToolExecutionResult;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.common.response.PageResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 开发阶段的模拟工具调用编排器。
 *
 * <p>该类模拟模型已经选定工具后的执行流程：权限校验、白名单匹配、参数校验、调用审计、幂等复用和异常收口。
 * 因此后续接入 Spring AI 时，模型层只需要负责选择工具和构造参数，安全与审计链路不必重写。</p>
 */
@Service
public class MockAgentToolCallingOrchestrator implements AgentToolCallingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MockAgentToolCallingOrchestrator.class);

    private final AgentToolExecutionAuthorizer authorizer;
    private final AgentToolRegistry toolRegistry;
    private final AgentToolCallAuditRepository auditRepository;
    private final InMemoryAgentToolResultCache resultCache;

    public MockAgentToolCallingOrchestrator(
            AgentToolExecutionAuthorizer authorizer,
            AgentToolRegistry toolRegistry,
            AgentToolCallAuditRepository auditRepository,
            InMemoryAgentToolResultCache resultCache
    ) {
        this.authorizer = authorizer;
        this.toolRegistry = toolRegistry;
        this.auditRepository = auditRepository;
        this.resultCache = resultCache;
    }

    @Override
    public synchronized AgentToolExecutionResponse execute(
            AgentToolExecutionContext context,
            AgentToolExecutionRequest request
    ) {
        authorizer.authorize(context);
        String requestId = normalizeRequestId(request.requestId());
        if (requestId != null) {
            AgentToolCallAudit existing = auditRepository.findSucceededByExecutionKey(
                    context.ownerUserId(), context.workspaceId(), requestId
            ).orElse(null);
            if (existing != null) {
                return new AgentToolExecutionResponse(
                        toSummary(existing),
                        resultCache.get(existing.getId()).orElse(null),
                        true
                );
            }
        }

        AgentToolCallAudit audit = createPendingAudit(context, request, requestId);
        long startedAtNanos = System.nanoTime();
        try {
            AgentTool tool = toolRegistry.requireTool(request.toolName());
            audit.setToolType(tool.definition().type());
            AgentToolExecutionResult result = tool.execute(context, request.arguments());
            audit.setStatus(AgentToolCallStatus.SUCCEEDED);
            audit.setResponseSummary(result.resultSummary());
            audit.setLatencyMs(elapsedMillis(startedAtNanos));
            auditRepository.save(audit);
            resultCache.put(audit.getId(), result.result());
            return new AgentToolExecutionResponse(toSummary(audit), result.result(), false);
        } catch (BusinessException exception) {
            markFailed(audit, startedAtNanos, exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            log.error("智能体工具调用失败，工具名称={}", request.toolName(), exception);
            markFailed(audit, startedAtNanos, "工具执行发生未预期异常");
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "智能体工具执行失败");
        }
    }

    @Override
    public PageResponse<AgentToolCallSummaryResponse> listAudits(
            AgentToolExecutionContext context,
            int page,
            int pageSize
    ) {
        authorizer.authorize(context);
        List<AgentToolCallAudit> audits = auditRepository.findByOwnerUserIdAndWorkspaceId(
                context.ownerUserId(), context.workspaceId()
        );
        int fromIndex = Math.min((page - 1) * pageSize, audits.size());
        int toIndex = Math.min(fromIndex + pageSize, audits.size());
        return new PageResponse<>(
                audits.subList(fromIndex, toIndex).stream().map(this::toSummary).toList(),
                page,
                pageSize,
                audits.size()
        );
    }

    private AgentToolCallAudit createPendingAudit(
            AgentToolExecutionContext context,
            AgentToolExecutionRequest request,
            String requestId
    ) {
        AgentToolCallAudit audit = new AgentToolCallAudit();
        audit.setOwnerUserId(context.ownerUserId());
        audit.setWorkspaceId(context.workspaceId());
        audit.setConversationId(context.conversationId());
        audit.setRequestId(requestId);
        audit.setToolName(request.toolName().trim());
        audit.setRequestPayload(summarizeArguments(request.arguments()));
        audit.setStatus(AgentToolCallStatus.PENDING);
        audit.setCreatedAt(OffsetDateTime.now());
        return auditRepository.save(audit);
    }

    private void markFailed(AgentToolCallAudit audit, long startedAtNanos, String errorMessage) {
        audit.setStatus(AgentToolCallStatus.FAILED);
        audit.setErrorMessage(errorMessage);
        audit.setLatencyMs(elapsedMillis(startedAtNanos));
        auditRepository.save(audit);
    }

    private AgentToolCallSummaryResponse toSummary(AgentToolCallAudit audit) {
        return new AgentToolCallSummaryResponse(
                audit.getId(),
                audit.getConversationId(),
                audit.getRequestId(),
                audit.getToolName(),
                audit.getToolType(),
                audit.getStatus(),
                audit.getRequestPayload(),
                audit.getResponseSummary(),
                audit.getErrorMessage(),
                audit.getLatencyMs(),
                audit.getCreatedAt()
        );
    }

    private String normalizeRequestId(String requestId) {
        return StringUtils.hasText(requestId) ? requestId.trim() : null;
    }

    private String summarizeArguments(JsonNode arguments) {
        if (arguments == null || !arguments.isObject()) {
            return "参数不是 JSON 对象";
        }
        List<String> fieldNames = new ArrayList<>();
        arguments.fieldNames().forEachRemaining(fieldNames::add);
        return "参数字段：" + String.join("、", fieldNames);
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
}
