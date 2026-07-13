package com.agentmind.chat.memory.service;

import com.agentmind.chat.memory.config.ChatMemoryProperties;
import com.agentmind.chat.memory.model.ChatConversation;
import com.agentmind.chat.memory.model.ChatMessage;
import com.agentmind.chat.memory.model.ChatMessageRole;
import com.agentmind.chat.memory.model.ChatMessageStatus;
import com.agentmind.chat.memory.repository.ChatMemoryRepository;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 短期会话记忆应用服务。
 *
 * <p>该服务统一处理会话归属验证、消息生命周期和滑动窗口。回答生成服务不直接访问仓储，
 * 后续替换为 Redis 时只需要新增仓储适配器。</p>
 */
@Service
public class ChatMemoryService {

    private static final int MAX_TITLE_LENGTH = 60;

    private final ChatMemoryRepository repository;
    private final ChatMemoryProperties properties;

    public ChatMemoryService(ChatMemoryRepository repository, ChatMemoryProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * 开始一轮问答，并返回开始前的短期历史窗口。
     */
    public ChatTurnContext beginTurn(Long workspaceId, Long requestedConversationId, String question) {
        ChatConversation conversation = resolveConversation(workspaceId, requestedConversationId, question);
        List<ChatMemoryEntry> history = buildWindow(repository.findRecentCompletedMessages(
                workspaceId,
                conversation.id(),
                Math.max(1, properties.getMaxMessages())
        ));
        ChatMessage userMessage = repository.createMessage(
                workspaceId,
                conversation.id(),
                ChatMessageRole.USER,
                ChatMessageStatus.COMPLETED,
                question
        );
        ChatMessage assistantMessage = repository.createMessage(
                workspaceId,
                conversation.id(),
                ChatMessageRole.ASSISTANT,
                ChatMessageStatus.PENDING,
                ""
        );
        return new ChatTurnContext(conversation, userMessage, assistantMessage, history);
    }

    public void completeAssistant(
            Long workspaceId,
            Long conversationId,
            Long assistantMessageId,
            String answer
    ) {
        transitionAssistant(
                workspaceId,
                conversationId,
                assistantMessageId,
                ChatMessageStatus.COMPLETED,
                answer,
                ""
        );
    }

    public void failAssistant(
            Long workspaceId,
            Long conversationId,
            Long assistantMessageId,
            String failureReason
    ) {
        transitionAssistant(
                workspaceId,
                conversationId,
                assistantMessageId,
                ChatMessageStatus.FAILED,
                "",
                normalizeReason(failureReason)
        );
    }

    public void cancelAssistant(
            Long workspaceId,
            Long conversationId,
            Long assistantMessageId,
            String cancellationReason
    ) {
        transitionAssistant(
                workspaceId,
                conversationId,
                assistantMessageId,
                ChatMessageStatus.CANCELLED,
                "",
                normalizeReason(cancellationReason)
        );
    }

    private ChatConversation resolveConversation(
            Long workspaceId,
            Long requestedConversationId,
            String question
    ) {
        if (requestedConversationId == null) {
            return repository.createConversation(workspaceId, buildTitle(question));
        }
        return repository.findConversationByWorkspaceIdAndId(workspaceId, requestedConversationId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "会话不存在或无权访问"
                ));
    }

    private List<ChatMemoryEntry> buildWindow(List<ChatMessage> completedMessages) {
        int maxContextChars = Math.max(1, properties.getMaxContextChars());
        Deque<ChatMemoryEntry> selected = new ArrayDeque<>();
        int usedChars = 0;

        for (int index = completedMessages.size() - 1; index >= 0; index--) {
            ChatMessage message = completedMessages.get(index);
            if (message.content() == null || message.content().isBlank()) {
                continue;
            }
            int remainingChars = maxContextChars - usedChars;
            if (remainingChars <= 0) {
                break;
            }
            String content = message.content();
            if (content.length() > remainingChars) {
                if (selected.isEmpty()) {
                    selected.addFirst(new ChatMemoryEntry(message.role(), content.substring(0, remainingChars)));
                }
                break;
            }
            selected.addFirst(new ChatMemoryEntry(message.role(), content));
            usedChars += content.length();
        }
        return List.copyOf(new ArrayList<>(selected));
    }

    private void transitionAssistant(
            Long workspaceId,
            Long conversationId,
            Long assistantMessageId,
            ChatMessageStatus targetStatus,
            String content,
            String failureReason
    ) {
        ChatMessage message = repository.findMessageByWorkspaceIdAndConversationIdAndId(
                workspaceId,
                conversationId,
                assistantMessageId
        ).orElseThrow(() -> new BusinessException(
                ErrorCode.RESOURCE_NOT_FOUND,
                "助手消息不存在或无权访问"
        ));
        if (message.role() != ChatMessageRole.ASSISTANT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目标消息不是助手消息");
        }
        repository.transitionPendingMessage(
                workspaceId,
                conversationId,
                assistantMessageId,
                targetStatus,
                content,
                failureReason
        );
    }

    private String buildTitle(String question) {
        String normalized = question == null ? "新会话" : question.strip().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            return "新会话";
        }
        return normalized.length() <= MAX_TITLE_LENGTH
                ? normalized
                : normalized.substring(0, MAX_TITLE_LENGTH);
    }

    private String normalizeReason(String reason) {
        return reason == null ? "" : reason;
    }
}
