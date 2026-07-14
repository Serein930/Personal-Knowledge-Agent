package com.agentmind.agent.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentmind.agent.confirmation.model.dto.CreateAgentToolConfirmationRequest;
import com.agentmind.agent.confirmation.model.dto.CreatedAgentToolConfirmationResponse;
import com.agentmind.agent.confirmation.service.AgentToolConfirmationApplicationService;
import com.agentmind.agent.proposal.config.WriteToolProposalProperties;
import com.agentmind.agent.tool.CreateFlashcardAgentTool;
import com.agentmind.agent.tool.CreateNoteAgentTool;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.chat.config.RagAnswerGenerationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/**
 * Spring AI 结构化写工具建议测试。
 *
 * <p>测试使用固定模型响应，不调用真实付费模型，重点验证强类型转换、白名单重组和规则降级。</p>
 */
class SpringAiStructuredWriteToolProposalServiceTests {

    private ChatModel chatModel;
    private AgentToolConfirmationApplicationService confirmationService;
    private WriteToolProposalProperties proposalProperties;
    private SpringAiStructuredWriteToolProposalService service;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        confirmationService = mock(AgentToolConfirmationApplicationService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        RagAnswerGenerationProperties ragProperties = new RagAnswerGenerationProperties();
        ragProperties.setWriteToolProposalsEnabled(true);
        proposalProperties = new WriteToolProposalProperties();
        proposalProperties.setFallbackToRuleEnabled(true);
        RuleBasedWriteToolProposalCandidateFactory fallbackFactory =
                new RuleBasedWriteToolProposalCandidateFactory(objectMapper);
        WriteToolProposalConfirmationCreator confirmationCreator =
                new WriteToolProposalConfirmationCreator(confirmationService);
        service = new SpringAiStructuredWriteToolProposalService(
                chatModel,
                ragProperties,
                proposalProperties,
                fallbackFactory,
                confirmationCreator,
                objectMapper
        );
        when(confirmationService.create(any(), any())).thenReturn(mock(CreatedAgentToolConfirmationResponse.class));
    }

    @Test
    void structuredFlashcardDecisionShouldCreatePendingConfirmationRequest() {
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("""
                {
                  "proposalRequired": true,
                  "toolName": "flashcard.create",
                  "title": null,
                  "content": null,
                  "question": "什么是独立失败审计事务？",
                  "answer": "主事务回滚时仍能提交失败记录的独立事务。",
                  "explanation": "用于保证故障可追踪。"
                }
                """));

        service.propose(context(), "生成一张复习卡片", "知识库回答");

        ArgumentCaptor<CreateAgentToolConfirmationRequest> requestCaptor =
                ArgumentCaptor.forClass(CreateAgentToolConfirmationRequest.class);
        verify(confirmationService).create(any(), requestCaptor.capture());
        CreateAgentToolConfirmationRequest request = requestCaptor.getValue();
        assertThat(request.toolName()).isEqualTo(CreateFlashcardAgentTool.TOOL_NAME);
        assertThat(request.requestId()).isEqualTo("sse-proposal-22-flashcard.create");
        assertThat(request.arguments().path("question").asText()).isEqualTo("什么是独立失败审计事务？");
        assertThat(request.arguments().path("answer").asText()).contains("主事务回滚");
    }

    @Test
    void noProposalDecisionShouldNotCreateConfirmation() {
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("""
                {
                  "proposalRequired": false,
                  "toolName": null,
                  "title": null,
                  "content": null,
                  "question": null,
                  "answer": null,
                  "explanation": null
                }
                """));

        assertThat(service.propose(context(), "解释事务传播", "知识库回答")).isEmpty();
        verify(confirmationService, never()).create(any(), any());
    }

    @Test
    void unknownModelToolShouldFallbackToRuleBasedWhitelist() {
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response("""
                {
                  "proposalRequired": true,
                  "toolName": "system.delete_all",
                  "title": null,
                  "content": null,
                  "question": "危险建议",
                  "answer": "危险内容",
                  "explanation": null
                }
                """));

        service.propose(context(), "请生成一张复习卡片", "安全的知识库回答");

        ArgumentCaptor<CreateAgentToolConfirmationRequest> requestCaptor =
                ArgumentCaptor.forClass(CreateAgentToolConfirmationRequest.class);
        verify(confirmationService).create(any(), requestCaptor.capture());
        assertThat(requestCaptor.getValue().toolName()).isEqualTo(CreateFlashcardAgentTool.TOOL_NAME);
        assertThat(requestCaptor.getValue().toolName()).isNotEqualTo(CreateNoteAgentTool.TOOL_NAME);
    }

    private AgentToolExecutionContext context() {
        return new AgentToolExecutionContext(1L, 9L, 11L, 22L);
    }

    private ChatResponse response(String content) {
        return new ChatResponse(java.util.List.of(new Generation(new AssistantMessage(content))));
    }
}
