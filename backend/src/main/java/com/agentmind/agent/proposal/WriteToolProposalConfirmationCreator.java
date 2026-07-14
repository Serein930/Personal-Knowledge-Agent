package com.agentmind.agent.proposal;

import com.agentmind.agent.confirmation.model.dto.CreateAgentToolConfirmationRequest;
import com.agentmind.agent.confirmation.model.dto.CreatedAgentToolConfirmationResponse;
import com.agentmind.agent.confirmation.service.AgentToolConfirmationApplicationService;
import com.agentmind.agent.proposal.model.WriteToolProposalCandidate;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 将已经校验的写工具候选转换为待确认单。
 *
 * <p>请求编号由消息编号和工具名称稳定生成，用于串联建议、确认、执行和审计记录。
 * 本组件只创建 {@code PENDING_CONFIRMATION}，不包含任何自动确认或执行入口。</p>
 */
@Component
public class WriteToolProposalConfirmationCreator {

    private final AgentToolConfirmationApplicationService confirmationService;

    public WriteToolProposalConfirmationCreator(AgentToolConfirmationApplicationService confirmationService) {
        this.confirmationService = confirmationService;
    }

    public List<CreatedAgentToolConfirmationResponse> create(
            AgentToolExecutionContext context,
            List<WriteToolProposalCandidate> candidates
    ) {
        return candidates.stream()
                .map(candidate -> confirmationService.create(
                        context,
                        new CreateAgentToolConfirmationRequest(
                                context.conversationId(),
                                context.messageId(),
                                candidate.toolName(),
                                requestId(context, candidate.toolName()),
                                candidate.arguments()
                        )
                ))
                .toList();
    }

    private String requestId(AgentToolExecutionContext context, String toolName) {
        return "sse-proposal-" + context.messageId() + "-" + toolName;
    }
}
