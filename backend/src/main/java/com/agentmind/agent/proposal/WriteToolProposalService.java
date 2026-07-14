package com.agentmind.agent.proposal;

import com.agentmind.agent.confirmation.model.dto.CreatedAgentToolConfirmationResponse;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import java.util.List;

/**
 * 写工具建议生成端口。
 *
 * <p>实现只能创建待确认单，不能执行写工具。后续可增加 Spring AI 建议生成适配器，
 * 但用户确认与真正执行仍由独立确认接口负责。</p>
 */
public interface WriteToolProposalService {

    List<CreatedAgentToolConfirmationResponse> propose(
            AgentToolExecutionContext context,
            String userQuestion,
            String generatedAnswer
    );
}
