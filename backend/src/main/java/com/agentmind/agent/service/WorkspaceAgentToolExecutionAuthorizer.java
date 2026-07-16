package com.agentmind.agent.service;

import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.chat.memory.repository.ChatMemoryRepository;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.workspace.service.WorkspaceAccessService;
import org.springframework.stereotype.Service;

/**
 * 基于真实知识空间成员关系的工具执行权限校验器。
 *
 * <p>除知识空间成员身份外，会话和消息仍使用联合归属查询，防止合法成员把其他空间的
 * 会话编号拼接到当前请求中。写工具的角色和确认权限继续由具体工具流程复核。</p>
 */
@Service
public class WorkspaceAgentToolExecutionAuthorizer implements AgentToolExecutionAuthorizer {

    private final ChatMemoryRepository chatMemoryRepository;
    private final WorkspaceAccessService workspaceAccessService;

    public WorkspaceAgentToolExecutionAuthorizer(
            ChatMemoryRepository chatMemoryRepository,
            WorkspaceAccessService workspaceAccessService
    ) {
        this.chatMemoryRepository = chatMemoryRepository;
        this.workspaceAccessService = workspaceAccessService;
    }

    @Override
    public void authorize(AgentToolExecutionContext context) {
        if (context == null || context.ownerUserId() == null || context.ownerUserId() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工具执行用户编号必须为正数");
        }
        if (context.workspaceId() == null || context.workspaceId() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "知识空间编号必须为正数");
        }
        workspaceAccessService.requireReadable(context.ownerUserId(), context.workspaceId());
        if (context.conversationId() != null) {
            chatMemoryRepository.findConversationByWorkspaceIdAndId(context.workspaceId(), context.conversationId())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESOURCE_NOT_FOUND, "会话不存在或无权访问"));
        }
        if (context.messageId() != null) {
            if (context.conversationId() == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "校验消息归属时必须同时提供会话编号");
            }
            chatMemoryRepository.findMessageByWorkspaceIdAndConversationIdAndId(
                            context.workspaceId(), context.conversationId(), context.messageId())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESOURCE_NOT_FOUND, "消息不存在或无权访问"));
        }
    }
}
