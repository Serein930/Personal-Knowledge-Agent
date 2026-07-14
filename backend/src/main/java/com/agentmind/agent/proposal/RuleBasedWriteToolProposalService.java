package com.agentmind.agent.proposal;

import com.agentmind.agent.confirmation.model.dto.CreatedAgentToolConfirmationResponse;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.chat.config.RagAnswerGenerationProperties;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 本地默认的写工具建议生成实现。
 *
 * <p>该实现使用稳定关键词保证测试可重复。真实模型适配器接入后，可依据结构化模型输出生成相同确认单契约，
 * 前端和确认执行链路无需变化。</p>
 */
@Service
@ConditionalOnProperty(
        prefix = "agentmind.agent.write-proposal",
        name = "generator",
        havingValue = "rule",
        matchIfMissing = true
)
public class RuleBasedWriteToolProposalService implements WriteToolProposalService {

    private final RagAnswerGenerationProperties properties;
    private final RuleBasedWriteToolProposalCandidateFactory candidateFactory;
    private final WriteToolProposalConfirmationCreator confirmationCreator;

    public RuleBasedWriteToolProposalService(
            RagAnswerGenerationProperties properties,
            RuleBasedWriteToolProposalCandidateFactory candidateFactory,
            WriteToolProposalConfirmationCreator confirmationCreator
    ) {
        this.properties = properties;
        this.candidateFactory = candidateFactory;
        this.confirmationCreator = confirmationCreator;
    }

    @Override
    public List<CreatedAgentToolConfirmationResponse> propose(
            AgentToolExecutionContext context,
            String userQuestion,
            String generatedAnswer
    ) {
        if (!properties.isWriteToolProposalsEnabled()) {
            return List.of();
        }
        return confirmationCreator.create(context, candidateFactory.create(userQuestion, generatedAnswer));
    }
}
