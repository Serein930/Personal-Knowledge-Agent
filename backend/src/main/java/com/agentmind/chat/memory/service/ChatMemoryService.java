package com.agentmind.chat.memory.service;

import com.agentmind.chat.memory.config.ChatMemoryProperties;
import com.agentmind.chat.memory.model.ChatConversation;
import com.agentmind.chat.memory.model.ChatConversationStatus;
import com.agentmind.chat.memory.model.ChatMessage;
import com.agentmind.chat.memory.model.ChatMessageRole;
import com.agentmind.chat.memory.model.ChatMessageStatus;
import com.agentmind.chat.memory.repository.ChatMemoryRepository;
import com.agentmind.chat.memory.token.ChatTokenCounter;
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
    private final ChatTokenCounter tokenCounter;

    public ChatMemoryService(
            ChatMemoryRepository repository,
            ChatMemoryProperties properties,
            ChatTokenCounter tokenCounter
    ) {
        this.repository = repository;
        this.properties = properties;
        this.tokenCounter = tokenCounter;
    }

    /**
     * 开始一轮问答，并返回开始前的短期历史窗口。
     */
    public ChatTurnContext beginTurn(Long workspaceId, Long requestedConversationId, String question) {
        ChatConversation conversation = resolveConversation(workspaceId, requestedConversationId, question);
        List<ChatMemoryEntry> history = buildWindow(repository.findRecentCompletedMessages(
                workspaceId,
                conversation.id(),
                Math.max(properties.getHistoryScanMessageLimit(), properties.getMaxHistoryTurns() * 2)
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
        ChatConversation conversation = repository.findConversationByWorkspaceIdAndId(workspaceId, requestedConversationId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "会话不存在或无权访问"
                ));
        if (conversation.status() == ChatConversationStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "归档会话不能继续问答");
        }
        return conversation;
    }

    private List<ChatMemoryEntry> buildWindow(List<ChatMessage> completedMessages) {
        List<CompletedChatTurn> completedTurns = pairCompletedTurns(completedMessages);
        Deque<CompletedChatTurn> selectedTurns = new ArrayDeque<>();
        int historyTokenBudget = properties.getModelContextWindowTokens()
                - properties.getReservedContextTokens();
        int usedTokens = 0;

        for (int index = completedTurns.size() - 1; index >= 0; index--) {
            if (selectedTurns.size() >= properties.getMaxHistoryTurns()) {
                break;
            }
            CompletedChatTurn turn = completedTurns.get(index);
            int turnTokens = tokenCounter.countTokens(ChatMessageRole.USER, turn.userMessage().content())
                    + tokenCounter.countTokens(ChatMessageRole.ASSISTANT, turn.assistantMessage().content());
            if (usedTokens + turnTokens > historyTokenBudget) {
                break;
            }
            selectedTurns.addFirst(turn);
            usedTokens += turnTokens;
        }

        List<ChatMemoryEntry> history = new ArrayList<>(selectedTurns.size() * 2);
        selectedTurns.forEach(turn -> {
            history.add(new ChatMemoryEntry(ChatMessageRole.USER, turn.userMessage().content()));
            history.add(new ChatMemoryEntry(ChatMessageRole.ASSISTANT, turn.assistantMessage().content()));
        });
        return List.copyOf(history);
    }

    /**
     * 将按时间排序的完成消息配对为完整问答轮次。连续用户消息只保留最靠近成功助手回答的一条，
     * 因而失败或取消回答对应的孤立用户消息不会污染后续提示词。
     */
    private List<CompletedChatTurn> pairCompletedTurns(List<ChatMessage> completedMessages) {
        List<CompletedChatTurn> turns = new ArrayList<>();
        ChatMessage pendingUserMessage = null;
        for (ChatMessage message : completedMessages) {
            if (message.content() == null || message.content().isBlank()) {
                continue;
            }
            if (message.role() == ChatMessageRole.USER) {
                pendingUserMessage = message;
                continue;
            }
            if (message.role() == ChatMessageRole.ASSISTANT && pendingUserMessage != null) {
                turns.add(new CompletedChatTurn(pendingUserMessage, message));
                pendingUserMessage = null;
            }
        }
        return List.copyOf(turns);
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

    private record CompletedChatTurn(ChatMessage userMessage, ChatMessage assistantMessage) {
    }
}
