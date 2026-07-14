package com.agentmind.agent.tool.springai;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.agent.audit.model.AgentToolCallStatus;
import com.agentmind.agent.audit.model.AgentToolType;
import com.agentmind.agent.audit.model.dto.AgentToolCallSummaryResponse;
import com.agentmind.agent.model.dto.AgentToolExecutionRequest;
import com.agentmind.agent.model.dto.AgentToolExecutionResponse;
import com.agentmind.agent.service.AgentToolCallingOrchestrator;
import com.agentmind.agent.tool.AgentTool;
import com.agentmind.agent.tool.AgentToolRegistry;
import com.agentmind.agent.tool.model.AgentToolDefinition;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.agent.tool.model.AgentToolExecutionResult;
import com.agentmind.common.response.PageResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

/**
 * Spring AI 工具回调适配器测试。
 */
class SpringAiAgentToolCallbackAdapterTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void adapterShouldExposeOnlyReadToolsAndUseModelSafeNames() throws Exception {
        RecordingOrchestrator orchestrator = new RecordingOrchestrator(objectMapper);
        AgentToolRegistry registry = new AgentToolRegistry(List.of(
                tool("knowledge.search", AgentToolType.READ),
                tool("note.create", AgentToolType.WRITE)
        ));
        SpringAiAgentToolCallbackAdapter adapter = new SpringAiAgentToolCallbackAdapter(
                registry,
                orchestrator,
                objectMapper
        );
        AgentToolExecutionContext context = new AgentToolExecutionContext(1L, 10L, 20L, 30L);

        List<ToolCallback> callbacks = adapter.createReadOnlyCallbacks(context);

        assertThat(callbacks).hasSize(1);
        ToolCallback callback = callbacks.getFirst();
        assertThat(callback.getToolDefinition().name()).isEqualTo("knowledge_search");
        assertThat(callback.getToolDefinition().inputSchema()).contains("query");
        assertThat(callback.call("{\"query\":\"线程池\"}"))
                .contains("线程池")
                .contains("workspaceId");
        assertThat(orchestrator.lastContext).isEqualTo(context);
        assertThat(orchestrator.lastRequest.toolName()).isEqualTo("knowledge.search");
    }

    private AgentTool tool(String name, AgentToolType type) {
        return new AgentTool() {
            @Override
            public AgentToolDefinition definition() {
                return new AgentToolDefinition(
                        name,
                        "测试工具",
                        type,
                        "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}"
                );
            }

            @Override
            public AgentToolExecutionResult execute(AgentToolExecutionContext context, JsonNode arguments) {
                throw new UnsupportedOperationException("适配器测试不直接执行领域工具");
            }
        };
    }

    private static final class RecordingOrchestrator implements AgentToolCallingOrchestrator {

        private final ObjectMapper objectMapper;
        private AgentToolExecutionContext lastContext;
        private AgentToolExecutionRequest lastRequest;

        private RecordingOrchestrator(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public AgentToolExecutionResponse execute(
                AgentToolExecutionContext context,
                AgentToolExecutionRequest request
        ) {
            this.lastContext = context;
            this.lastRequest = request;
            AgentToolCallSummaryResponse summary = summary(request.toolName());
            JsonNode result = objectMapper.createObjectNode()
                    .put("query", request.arguments().path("query").asText())
                    .put("workspaceId", context.workspaceId());
            return new AgentToolExecutionResponse(summary, result, false);
        }

        @Override
        public AgentToolExecutionResponse executeConfirmedWrite(
                AgentToolExecutionContext context,
                AgentToolExecutionRequest request
        ) {
            throw new UnsupportedOperationException("适配器测试不执行已确认写工具");
        }

        @Override
        public PageResponse<AgentToolCallSummaryResponse> listAudits(
                AgentToolExecutionContext context,
                int page,
                int pageSize
        ) {
            return new PageResponse<>(List.of(), page, pageSize, 0);
        }

        @Override
        public List<AgentToolCallSummaryResponse> findAuditsForExecution(AgentToolExecutionContext context) {
            return List.of();
        }

        private AgentToolCallSummaryResponse summary(String toolName) {
            return new AgentToolCallSummaryResponse(
                    toolName,
                    AgentToolType.READ,
                    AgentToolCallStatus.SUCCEEDED,
                    "测试调用成功",
                    1
            );
        }
    }
}
