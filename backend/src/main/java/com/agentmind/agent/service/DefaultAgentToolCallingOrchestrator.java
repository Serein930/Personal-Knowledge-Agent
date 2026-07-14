package com.agentmind.agent.service;

import com.agentmind.agent.audit.model.AgentToolCallAudit;
import com.agentmind.agent.audit.model.AgentToolCallStatus;
import com.agentmind.agent.audit.model.AgentToolType;
import com.agentmind.agent.audit.model.dto.AgentToolCallSummaryResponse;
import com.agentmind.agent.audit.repository.AgentToolCallAuditRepository;
import com.agentmind.agent.audit.service.AgentToolFailureAuditRecorder;
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
import com.fasterxml.jackson.databind.node.TextNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 智能体工具调用默认编排器。
 *
 * <p>该类处理模型或显式接口已经选定工具后的统一执行流程：权限校验、白名单匹配、参数校验、
 * 调用审计、幂等复用和异常收口。Spring AI 与 REST 接口均复用本编排器。</p>
 */
@Service
public class DefaultAgentToolCallingOrchestrator implements AgentToolCallingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentToolCallingOrchestrator.class);

    private final AgentToolExecutionAuthorizer authorizer;
    private final AgentToolRegistry toolRegistry;
    private final AgentToolCallAuditRepository auditRepository;
    private final AgentToolFailureAuditRecorder failureAuditRecorder;
    private final InMemoryAgentToolResultCache resultCache;

    public DefaultAgentToolCallingOrchestrator(
            AgentToolExecutionAuthorizer authorizer,
            AgentToolRegistry toolRegistry,
            AgentToolCallAuditRepository auditRepository,
            AgentToolFailureAuditRecorder failureAuditRecorder,
            InMemoryAgentToolResultCache resultCache
    ) {
        this.authorizer = authorizer;
        this.toolRegistry = toolRegistry;
        this.auditRepository = auditRepository;
        this.failureAuditRecorder = failureAuditRecorder;
        this.resultCache = resultCache;
    }

    @Override
    public synchronized AgentToolExecutionResponse execute(
            AgentToolExecutionContext context,
            AgentToolExecutionRequest request
    ) {
        return executeInternal(context, request, false);
    }

    @Override
    public synchronized AgentToolExecutionResponse executeConfirmedWrite(
            AgentToolExecutionContext context,
            AgentToolExecutionRequest request
    ) {
        return executeInternal(context, request, true);
    }

    private AgentToolExecutionResponse executeInternal(
            AgentToolExecutionContext context,
            AgentToolExecutionRequest request,
            boolean confirmedWrite
    ) {
        authorizer.authorize(context);
        String requestId = normalizeRequestId(request.requestId());
        String requestFingerprint = fingerprintArguments(request.arguments());
        if (requestId != null) {
            AgentToolCallAudit existing = auditRepository.findSucceededByExecutionKey(
                    context.ownerUserId(), context.workspaceId(), request.toolName().trim(), requestId
            ).orElse(null);
            if (existing != null) {
                if (!requestFingerprint.equals(existing.getRequestFingerprint())) {
                    throw new BusinessException(
                            ErrorCode.RESOURCE_CONFLICT,
                            "相同请求编号不能用于不同的工具参数"
                    );
                }
                return new AgentToolExecutionResponse(
                        toSummary(existing),
                        resultCache.get(existing.getId()).orElse(null),
                        true
                );
            }
        }

        AgentToolCallAudit audit = createPendingAudit(context, request, requestId, requestFingerprint);
        long startedAtNanos = System.nanoTime();
        try {
            AgentTool tool = toolRegistry.requireTool(request.toolName());
            audit.setToolType(tool.definition().type());
            verifyExecutionChannel(tool, confirmedWrite);
            tool.validateArguments(request.arguments());
            AgentToolExecutionResult result = tool.execute(context.withRequestId(requestId), request.arguments());
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

    private void verifyExecutionChannel(AgentTool tool, boolean confirmedWrite) {
        if (confirmedWrite && tool.definition().type() != AgentToolType.WRITE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "确认执行入口只允许调用写工具");
        }
        if (!confirmedWrite && tool.definition().type() == AgentToolType.WRITE) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "写工具必须先创建确认单并完成用户确认");
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

    @Override
    public List<AgentToolCallSummaryResponse> findAuditsForExecution(AgentToolExecutionContext context) {
        authorizer.authorize(context);
        if (context.conversationId() == null || context.messageId() == null) {
            return List.of();
        }
        return auditRepository.findByExecutionContext(
                        context.ownerUserId(),
                        context.workspaceId(),
                        context.conversationId(),
                        context.messageId()
                ).stream()
                .map(this::toSummary)
                .toList();
    }

    private AgentToolCallAudit createPendingAudit(
            AgentToolExecutionContext context,
            AgentToolExecutionRequest request,
            String requestId,
            String requestFingerprint
    ) {
        AgentToolCallAudit audit = new AgentToolCallAudit();
        audit.setOwnerUserId(context.ownerUserId());
        audit.setWorkspaceId(context.workspaceId());
        audit.setConversationId(context.conversationId());
        audit.setMessageId(context.messageId());
        audit.setRequestId(requestId);
        audit.setToolName(request.toolName().trim());
        audit.setRequestPayload(summarizeArguments(request.arguments()));
        audit.setRequestFingerprint(requestFingerprint);
        audit.setStatus(AgentToolCallStatus.PENDING);
        audit.setCreatedAt(OffsetDateTime.now());
        return audit;
    }

    private void markFailed(AgentToolCallAudit audit, long startedAtNanos, String errorMessage) {
        failureAuditRecorder.recordFailure(audit, elapsedMillis(startedAtNanos), errorMessage);
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

    private String fingerprintArguments(JsonNode arguments) {
        String canonicalValue = canonicalize(arguments);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalValue.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 运行环境不支持 SHA-256", exception);
        }
    }

    /**
     * 按字段名排序生成稳定 JSON，避免相同对象仅因字段提交顺序不同而得到不同请求指纹。
     */
    private String canonicalize(JsonNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.isObject()) {
            List<String> fieldNames = new ArrayList<>();
            node.fieldNames().forEachRemaining(fieldNames::add);
            Collections.sort(fieldNames);
            StringJoiner fields = new StringJoiner(",", "{", "}");
            for (String fieldName : fieldNames) {
                fields.add(new TextNode(fieldName) + ":" + canonicalize(node.get(fieldName)));
            }
            return fields.toString();
        }
        if (node.isArray()) {
            StringJoiner values = new StringJoiner(",", "[", "]");
            node.forEach(value -> values.add(canonicalize(value)));
            return values.toString();
        }
        return node.toString();
    }
}
