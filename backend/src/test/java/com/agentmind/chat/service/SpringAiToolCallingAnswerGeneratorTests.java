package com.agentmind.chat.service;

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
import com.agentmind.agent.tool.springai.SpringAiAgentToolCallbackAdapter;
import com.agentmind.chat.config.RagAnswerGenerationProperties;
import com.agentmind.chat.repository.InMemoryRagModelCallObservationRepository;
import com.agentmind.common.response.PageResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Spring AI 自动工具选择与最终回答生成测试。
 *
 * <p>假模型第一轮返回工具调用，Spring AI 应执行回调并把工具结果加入会话，
 * 第二轮再生成最终回答。整个测试不访问真实模型。</p>
 */
class SpringAiToolCallingAnswerGeneratorTests {

    @Test
    void generatorShouldExecuteModelSelectedToolAndReturnAuditSummary() {
        ObjectMapper objectMapper = new ObjectMapper();
        RecordingOrchestrator orchestrator = new RecordingOrchestrator(objectMapper);
        AgentToolRegistry registry = new AgentToolRegistry(List.of(readOnlySearchTool()));
        SpringAiAgentToolCallbackAdapter adapter = new SpringAiAgentToolCallbackAdapter(
                registry,
                orchestrator,
                objectMapper
        );
        ToolChoosingChatModel chatModel = new ToolChoosingChatModel();
        RagAnswerGenerationProperties properties = new RagAnswerGenerationProperties();
        properties.setSpringAiFailureFallbackEnabled(false);
        properties.setToolCallingEnabled(true);
        SpringAiChatModelAnswerGenerator generator = new SpringAiChatModelAnswerGenerator(
                chatModel,
                properties,
                new RagModelCallLogger(new InMemoryRagModelCallObservationRepository()),
                adapter,
                orchestrator
        );

        GeneratedAnswer answer = generator.generate(new AnswerGenerationRequest(
                1L,
                1L,
                101L,
                201L,
                "线程池为什么复用线程？",
                "rag-chat-v1",
                "检索上下文",
                "请基于工具回答线程池问题",
                List.of(),
                new RagRefusalDecision(false, "")
        ));

        assertThat(chatModel.callCount.get()).isEqualTo(2);
        assertThat(answer.content()).isEqualTo("根据工具结果，线程池通过复用工作线程降低创建开销。");
        assertThat(answer.toolCalls()).hasSize(1);
        assertThat(answer.toolCalls().getFirst().toolName()).isEqualTo("knowledge.search");
        assertThat(answer.toolCalls().getFirst().status()).isEqualTo(AgentToolCallStatus.SUCCEEDED);
        assertThat(orchestrator.lastContext.messageId()).isEqualTo(201L);
    }

    private AgentTool readOnlySearchTool() {
        return new AgentTool() {
            @Override
            public AgentToolDefinition definition() {
                return new AgentToolDefinition(
                        "knowledge.search",
                        "检索当前知识空间",
                        AgentToolType.READ,
                        """
                                {
                                  "type":"object",
                                  "properties":{"query":{"type":"string"}},
                                  "required":["query"]
                                }
                                """
                );
            }

            @Override
            public AgentToolExecutionResult execute(AgentToolExecutionContext context, JsonNode arguments) {
                throw new UnsupportedOperationException("模型回调统一经过编排器，不直接执行此测试工具");
            }
        };
    }

    private static final class ToolChoosingChatModel implements ChatModel {

        private final AtomicInteger callCount = new AtomicInteger();

        @Override
        public ChatResponse call(Prompt prompt) {
            if (callCount.incrementAndGet() == 1) {
                AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                        "call-1",
                        "function",
                        "knowledge_search",
                        "{\"query\":\"线程池复用线程\"}"
                );
                AssistantMessage message = new AssistantMessage("", Map.of(), List.of(toolCall));
                return new ChatResponse(List.of(new Generation(message)));
            }
            return new ChatResponse(List.of(new Generation(
                    new AssistantMessage("根据工具结果，线程池通过复用工作线程降低创建开销。")
            )));
        }
    }

    private static final class RecordingOrchestrator implements AgentToolCallingOrchestrator {

        private final ObjectMapper objectMapper;
        private final List<AgentToolCallSummaryResponse> audits = new ArrayList<>();
        private AgentToolExecutionContext lastContext;

        private RecordingOrchestrator(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public AgentToolExecutionResponse execute(
                AgentToolExecutionContext context,
                AgentToolExecutionRequest request
        ) {
            this.lastContext = context;
            AgentToolCallSummaryResponse summary = new AgentToolCallSummaryResponse(
                    request.toolName(),
                    AgentToolType.READ,
                    AgentToolCallStatus.SUCCEEDED,
                    "知识检索完成，返回1个片段",
                    1
            );
            audits.add(summary);
            JsonNode result = objectMapper.createObjectNode()
                    .put("content", "线程池复用工作线程并降低创建开销");
            return new AgentToolExecutionResponse(summary, result, false);
        }

        @Override
        public AgentToolExecutionResponse executeConfirmedWrite(
                AgentToolExecutionContext context,
                AgentToolExecutionRequest request
        ) {
            throw new UnsupportedOperationException("回答生成测试不执行已确认写工具");
        }

        @Override
        public PageResponse<AgentToolCallSummaryResponse> listAudits(
                AgentToolExecutionContext context,
                int page,
                int pageSize
        ) {
            return new PageResponse<>(List.copyOf(audits), page, pageSize, audits.size());
        }

        @Override
        public List<AgentToolCallSummaryResponse> findAuditsForExecution(AgentToolExecutionContext context) {
            return List.copyOf(audits);
        }
    }
}
