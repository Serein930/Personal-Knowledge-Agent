package com.agentmind.chat.memory.repository;

import com.agentmind.chat.memory.model.ChatConversation;
import com.agentmind.chat.memory.model.ChatMessage;
import com.agentmind.chat.memory.model.ChatMessageRole;
import com.agentmind.chat.memory.model.ChatMessageStatus;
import java.util.List;
import java.util.Optional;

/**
 * 短期会话记忆存储端口。
 *
 * <p>应用服务只依赖该接口。默认内存适配器用于开发和测试，后续 Redis 适配器必须保持相同的
 * 知识空间隔离、消息顺序、分页和最终状态更新语义。</p>
 */
public interface ChatMemoryRepository {

    ChatConversation createConversation(Long workspaceId, String title);

    Optional<ChatConversation> findConversationByWorkspaceIdAndId(Long workspaceId, Long conversationId);

    List<ChatConversation> findConversationsByWorkspaceId(Long workspaceId, int offset, int limit);

    long countConversationsByWorkspaceId(Long workspaceId);

    ChatMessage createMessage(
            Long workspaceId,
            Long conversationId,
            ChatMessageRole role,
            ChatMessageStatus status,
            String content
    );

    Optional<ChatMessage> findMessageByWorkspaceIdAndConversationIdAndId(
            Long workspaceId,
            Long conversationId,
            Long messageId
    );

    Optional<ChatMessage> transitionPendingMessage(
            Long workspaceId,
            Long conversationId,
            Long messageId,
            ChatMessageStatus targetStatus,
            String content,
            String failureReason
    );

    List<ChatMessage> findMessagesByWorkspaceIdAndConversationId(
            Long workspaceId,
            Long conversationId,
            int offset,
            int limit
    );

    long countMessagesByWorkspaceIdAndConversationId(Long workspaceId, Long conversationId);

    List<ChatMessage> findRecentCompletedMessages(
            Long workspaceId,
            Long conversationId,
            int limit
    );
}
