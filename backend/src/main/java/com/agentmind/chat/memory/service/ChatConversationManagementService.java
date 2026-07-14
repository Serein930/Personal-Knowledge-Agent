package com.agentmind.chat.memory.service;

import com.agentmind.chat.memory.model.ChatConversation;
import com.agentmind.chat.memory.model.dto.ChatConversationResponse;
import com.agentmind.chat.memory.repository.ChatMemoryRepository;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * 会话生命周期管理服务。
 *
 * <p>该服务统一执行标题规范化、知识空间归属判断和资源不存在语义。控制层不直接调用仓储，
 * 内存与 Redis 的实现差异被限制在基础设施适配器内部。</p>
 */
@Service
public class ChatConversationManagementService {

    private final ChatMemoryRepository repository;

    public ChatConversationManagementService(ChatMemoryRepository repository) {
        this.repository = repository;
    }

    public ChatConversationResponse rename(Long workspaceId, Long conversationId, String title) {
        String normalizedTitle = title.strip().replaceAll("\\s+", " ");
        ChatConversation conversation = repository.renameConversation(
                workspaceId,
                conversationId,
                normalizedTitle
        ).orElseThrow(this::conversationNotFound);
        return toResponse(conversation);
    }

    public ChatConversationResponse archive(Long workspaceId, Long conversationId) {
        ChatConversation conversation = repository.archiveConversation(workspaceId, conversationId)
                .orElseThrow(this::conversationNotFound);
        return toResponse(conversation);
    }

    public void delete(Long workspaceId, Long conversationId) {
        if (!repository.deleteConversation(workspaceId, conversationId)) {
            throw conversationNotFound();
        }
    }

    private BusinessException conversationNotFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "会话不存在或无权访问");
    }

    private ChatConversationResponse toResponse(ChatConversation conversation) {
        return new ChatConversationResponse(
                conversation.id(),
                conversation.title(),
                conversation.status(),
                conversation.createdAt(),
                conversation.updatedAt()
        );
    }
}
