package com.agentmind.chat.memory.service;

import com.agentmind.chat.memory.model.ChatConversation;
import com.agentmind.chat.memory.model.ChatMessage;
import com.agentmind.chat.memory.model.dto.ChatConversationResponse;
import com.agentmind.chat.memory.model.dto.ChatMessageResponse;
import com.agentmind.chat.memory.repository.ChatMemoryRepository;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.common.response.PageResponse;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 短期会话记忆查询服务。
 *
 * <p>所有消息查询都会先使用知识空间编号与会话编号联合验证归属。对其他知识空间中的真实会话，
 * 接口同样返回资源不存在，避免通过响应差异探测用户数据。</p>
 */
@Service
public class ChatMemoryQueryService {

    private final ChatMemoryRepository repository;

    public ChatMemoryQueryService(ChatMemoryRepository repository) {
        this.repository = repository;
    }

    public PageResponse<ChatConversationResponse> listConversations(
            Long workspaceId,
            int page,
            int pageSize
    ) {
        int offset = Math.multiplyExact(page - 1, pageSize);
        List<ChatConversationResponse> records = repository
                .findConversationsByWorkspaceId(workspaceId, offset, pageSize)
                .stream()
                .map(this::toConversationResponse)
                .toList();
        return new PageResponse<>(records, page, pageSize, repository.countConversationsByWorkspaceId(workspaceId));
    }

    public PageResponse<ChatMessageResponse> listMessages(
            Long workspaceId,
            Long conversationId,
            int page,
            int pageSize
    ) {
        requireConversation(workspaceId, conversationId);
        int offset = Math.multiplyExact(page - 1, pageSize);
        List<ChatMessageResponse> records = repository
                .findMessagesByWorkspaceIdAndConversationId(workspaceId, conversationId, offset, pageSize)
                .stream()
                .map(this::toMessageResponse)
                .toList();
        long total = repository.countMessagesByWorkspaceIdAndConversationId(workspaceId, conversationId);
        return new PageResponse<>(records, page, pageSize, total);
    }

    private void requireConversation(Long workspaceId, Long conversationId) {
        repository.findConversationByWorkspaceIdAndId(workspaceId, conversationId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "会话不存在或无权访问"
                ));
    }

    private ChatConversationResponse toConversationResponse(ChatConversation conversation) {
        return new ChatConversationResponse(
                conversation.id(),
                conversation.title(),
                conversation.status(),
                conversation.createdAt(),
                conversation.updatedAt()
        );
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.id(),
                message.role(),
                message.status(),
                message.content(),
                message.failureReason(),
                message.createdAt(),
                message.updatedAt()
        );
    }
}
