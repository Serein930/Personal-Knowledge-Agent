package com.agentmind.agent.proposal;

import com.agentmind.agent.confirmation.model.dto.CreateAgentToolConfirmationRequest;
import com.agentmind.agent.confirmation.model.dto.CreatedAgentToolConfirmationResponse;
import com.agentmind.agent.confirmation.service.AgentToolConfirmationApplicationService;
import com.agentmind.agent.tool.CreateFlashcardAgentTool;
import com.agentmind.agent.tool.CreateNoteAgentTool;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.chat.config.RagAnswerGenerationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 本地默认的写工具建议生成实现。
 *
 * <p>该实现使用稳定关键词保证测试可重复。真实模型适配器接入后，可依据结构化模型输出生成相同确认单契约，
 * 前端和确认执行链路无需变化。</p>
 */
@Service
public class RuleBasedWriteToolProposalService implements WriteToolProposalService {

    private final AgentToolConfirmationApplicationService confirmationService;
    private final RagAnswerGenerationProperties properties;
    private final ObjectMapper objectMapper;

    public RuleBasedWriteToolProposalService(
            AgentToolConfirmationApplicationService confirmationService,
            RagAnswerGenerationProperties properties,
            ObjectMapper objectMapper
    ) {
        this.confirmationService = confirmationService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<CreatedAgentToolConfirmationResponse> propose(
            AgentToolExecutionContext context,
            String userQuestion,
            String generatedAnswer
    ) {
        if (!properties.isWriteToolProposalsEnabled() || !StringUtils.hasText(generatedAnswer)) {
            return List.of();
        }
        if (containsAny(userQuestion, "复习卡片", "闪卡", "记忆卡片")) {
            return List.of(createFlashcardProposal(context, userQuestion, generatedAnswer));
        }
        if (containsAny(userQuestion, "创建笔记", "保存笔记", "整理成笔记", "记录成笔记")) {
            return List.of(createNoteProposal(context, userQuestion, generatedAnswer));
        }
        return List.of();
    }

    private CreatedAgentToolConfirmationResponse createFlashcardProposal(
            AgentToolExecutionContext context,
            String userQuestion,
            String generatedAnswer
    ) {
        ObjectNode arguments = objectMapper.createObjectNode()
                .put("question", truncate(userQuestion, 500))
                .put("answer", truncate(generatedAnswer, 10_000))
                .put("explanation", "根据本次知识库回答生成，确认后保存到当前知识空间。");
        return createConfirmation(context, CreateFlashcardAgentTool.TOOL_NAME, arguments);
    }

    private CreatedAgentToolConfirmationResponse createNoteProposal(
            AgentToolExecutionContext context,
            String userQuestion,
            String generatedAnswer
    ) {
        ObjectNode arguments = objectMapper.createObjectNode()
                .put("title", truncate(userQuestion, 120))
                .put("content", truncate(generatedAnswer, 20_000));
        return createConfirmation(context, CreateNoteAgentTool.TOOL_NAME, arguments);
    }

    private CreatedAgentToolConfirmationResponse createConfirmation(
            AgentToolExecutionContext context,
            String toolName,
            ObjectNode arguments
    ) {
        String requestId = "sse-proposal-" + context.messageId() + "-" + toolName;
        return confirmationService.create(
                context,
                new CreateAgentToolConfirmationRequest(
                        context.conversationId(), context.messageId(), toolName, requestId, arguments
                )
        );
    }

    private boolean containsAny(String value, String... keywords) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
