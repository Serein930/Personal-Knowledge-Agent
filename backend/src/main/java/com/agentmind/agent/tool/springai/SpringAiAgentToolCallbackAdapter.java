package com.agentmind.agent.tool.springai;

import com.agentmind.agent.audit.model.AgentToolType;
import com.agentmind.agent.model.dto.AgentToolExecutionRequest;
import com.agentmind.agent.model.dto.AgentToolExecutionResponse;
import com.agentmind.agent.service.AgentToolCallingOrchestrator;
import com.agentmind.agent.tool.AgentTool;
import com.agentmind.agent.tool.AgentToolRegistry;
import com.agentmind.agent.tool.model.AgentToolDefinition;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

/**
 * 将项目内部工具注册表转换为 Spring AI Tool Callback 的适配器。
 *
 * <p>模型只会看到只读工具。模型返回的参数仍需经过既有编排器，因此无法绕过工具白名单、
 * 用户与知识空间校验、参数校验和调用审计。未来写工具必须经过用户确认后才能单独加入模型白名单。</p>
 */
@Component
public class SpringAiAgentToolCallbackAdapter {

    public static final String CONTEXT_OWNER_USER_ID = "agentmindOwnerUserId";
    public static final String CONTEXT_WORKSPACE_ID = "agentmindWorkspaceId";
    public static final String CONTEXT_CONVERSATION_ID = "agentmindConversationId";
    public static final String CONTEXT_MESSAGE_ID = "agentmindMessageId";

    private final AgentToolRegistry toolRegistry;
    private final AgentToolCallingOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

    public SpringAiAgentToolCallbackAdapter(
            AgentToolRegistry toolRegistry,
            AgentToolCallingOrchestrator orchestrator,
            ObjectMapper objectMapper
    ) {
        this.toolRegistry = toolRegistry;
        this.orchestrator = orchestrator;
        this.objectMapper = objectMapper;
    }

    /**
     * 为本次回答构造带有可信执行上下文的只读工具回调。
     */
    public List<ToolCallback> createReadOnlyCallbacks(AgentToolExecutionContext context) {
        Map<String, String> modelNames = new LinkedHashMap<>();
        return toolRegistry.registeredTools().stream()
                .filter(tool -> tool.definition().type() == AgentToolType.READ)
                .map(tool -> createCallback(tool, context, modelNames))
                .toList();
    }

    public Map<String, Object> createToolContext(AgentToolExecutionContext context) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(CONTEXT_OWNER_USER_ID, context.ownerUserId());
        values.put(CONTEXT_WORKSPACE_ID, context.workspaceId());
        if (context.conversationId() != null) {
            values.put(CONTEXT_CONVERSATION_ID, context.conversationId());
        }
        if (context.messageId() != null) {
            values.put(CONTEXT_MESSAGE_ID, context.messageId());
        }
        return Map.copyOf(values);
    }

    private ToolCallback createCallback(
            AgentTool tool,
            AgentToolExecutionContext context,
            Map<String, String> modelNames
    ) {
        AgentToolDefinition definition = tool.definition();
        String modelName = toModelSafeName(definition.name());
        String existing = modelNames.putIfAbsent(modelName, definition.name());
        if (existing != null) {
            throw new IllegalStateException("工具名称转换后发生冲突：" + existing + " 与 " + definition.name());
        }
        ToolDefinition springDefinition = new DefaultToolDefinition(
                modelName,
                definition.description(),
                definition.inputSchema()
        );
        return new RegisteredAgentToolCallback(springDefinition, definition.name(), context);
    }

    private String toModelSafeName(String internalName) {
        return internalName.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "_");
    }

    private final class RegisteredAgentToolCallback implements ToolCallback {

        private final ToolDefinition toolDefinition;
        private final String internalToolName;
        private final AgentToolExecutionContext executionContext;

        private RegisteredAgentToolCallback(
                ToolDefinition toolDefinition,
                String internalToolName,
                AgentToolExecutionContext executionContext
        ) {
            this.toolDefinition = toolDefinition;
            this.internalToolName = internalToolName;
            this.executionContext = executionContext;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return toolDefinition;
        }

        @Override
        public String call(String toolInput) {
            return execute(toolInput);
        }

        @Override
        public String call(String toolInput, ToolContext ignoredToolContext) {
            // 使用创建回调时捕获的可信上下文，不接受模型篡改用户、知识空间或会话归属。
            return execute(toolInput);
        }

        private String execute(String toolInput) {
            try {
                JsonNode arguments = objectMapper.readTree(toolInput);
                AgentToolExecutionResponse response = orchestrator.execute(
                        executionContext,
                        new AgentToolExecutionRequest(
                                executionContext.conversationId(),
                                internalToolName,
                                "spring-ai-" + UUID.randomUUID(),
                                arguments
                        )
                );
                return objectMapper.writeValueAsString(response.result());
            } catch (JsonProcessingException exception) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "模型生成的工具参数不是合法 JSON");
            }
        }
    }
}
