package com.agentmind.agent.service;

import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.chat.memory.repository.ChatMemoryRepository;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * 未接入登录系统前的演示权限校验实现。
 *
 * <p>当前项目尚未具备真实用户和知识空间成员持久化关系，因此只允许演示用户编号 1 调用工具。
 * 对会话上下文仍强制使用“知识空间编号 + 会话编号”联合查询，避免跨知识空间会话被工具使用。
 * 接入 Spring Security 与成员表后，只替换本类即可。</p>
 */
@Service
public class DemoAgentToolExecutionAuthorizer implements AgentToolExecutionAuthorizer {

    public static final long DEMO_USER_ID = 1L;

    private final ChatMemoryRepository chatMemoryRepository;

    public DemoAgentToolExecutionAuthorizer(ChatMemoryRepository chatMemoryRepository) {
        this.chatMemoryRepository = chatMemoryRepository;
    }

    @Override
    public void authorize(AgentToolExecutionContext context) {
        if (context == null || context.ownerUserId() == null || context.ownerUserId() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工具执行用户编号必须为正数");
        }
        if (context.workspaceId() == null || context.workspaceId() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "知识空间编号必须为正数");
        }
        if (context.ownerUserId() != DEMO_USER_ID) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前演示环境不允许该用户调用智能体工具");
        }
        if (context.conversationId() != null) {
            chatMemoryRepository.findConversationByWorkspaceIdAndId(context.workspaceId(), context.conversationId())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESOURCE_NOT_FOUND,
                            "会话不存在或无权访问"
                    ));
        }
    }
}
