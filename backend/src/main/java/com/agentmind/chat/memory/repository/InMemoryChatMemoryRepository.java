package com.agentmind.chat.memory.repository;

import com.agentmind.chat.memory.model.ChatConversation;
import com.agentmind.chat.memory.model.ChatConversationStatus;
import com.agentmind.chat.memory.model.ChatMessage;
import com.agentmind.chat.memory.model.ChatMessageRole;
import com.agentmind.chat.memory.model.ChatMessageStatus;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * 短期会话记忆的内存适配器。
 *
 * <p>并发映射表用于支持本地多请求联调，编号生成器保证单进程内唯一。服务重启后数据会清空，
 * 因此该实现不承担生产持久化职责。</p>
 */
@Repository
@ConditionalOnProperty(
        prefix = "agentmind.chat.memory",
        name = "store",
        havingValue = "memory",
        matchIfMissing = true
)
public class InMemoryChatMemoryRepository implements ChatMemoryRepository {

    private final AtomicLong conversationIdGenerator = new AtomicLong(1_000);
    private final AtomicLong messageIdGenerator = new AtomicLong(10_000);
    private final ConcurrentHashMap<Long, ChatConversation> conversations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ChatMessage> messages = new ConcurrentHashMap<>();

    @Override
    public ChatConversation createConversation(Long workspaceId, String title) {
        OffsetDateTime now = OffsetDateTime.now();
        ChatConversation conversation = new ChatConversation(
                conversationIdGenerator.incrementAndGet(),
                workspaceId,
                title,
                ChatConversationStatus.ACTIVE,
                now,
                now
        );
        conversations.put(conversation.id(), conversation);
        return conversation;
    }

    @Override
    public Optional<ChatConversation> findConversationByWorkspaceIdAndId(Long workspaceId, Long conversationId) {
        return Optional.ofNullable(conversations.get(conversationId))
                .filter(conversation -> conversation.workspaceId().equals(workspaceId));
    }

    @Override
    public synchronized Optional<ChatConversation> renameConversation(
            Long workspaceId,
            Long conversationId,
            String title
    ) {
        AtomicReference<ChatConversation> result = new AtomicReference<>();
        conversations.computeIfPresent(conversationId, (ignored, existing) -> {
            if (!existing.workspaceId().equals(workspaceId)) {
                return existing;
            }
            ChatConversation renamed = new ChatConversation(
                    existing.id(),
                    existing.workspaceId(),
                    title,
                    existing.status(),
                    existing.createdAt(),
                    OffsetDateTime.now()
            );
            result.set(renamed);
            return renamed;
        });
        return Optional.ofNullable(result.get());
    }

    @Override
    public synchronized Optional<ChatConversation> archiveConversation(Long workspaceId, Long conversationId) {
        AtomicReference<ChatConversation> result = new AtomicReference<>();
        conversations.computeIfPresent(conversationId, (ignored, existing) -> {
            if (!existing.workspaceId().equals(workspaceId)) {
                return existing;
            }
            if (existing.status() == ChatConversationStatus.ARCHIVED) {
                result.set(existing);
                return existing;
            }
            ChatConversation archived = new ChatConversation(
                    existing.id(),
                    existing.workspaceId(),
                    existing.title(),
                    ChatConversationStatus.ARCHIVED,
                    existing.createdAt(),
                    OffsetDateTime.now()
            );
            result.set(archived);
            return archived;
        });
        return Optional.ofNullable(result.get());
    }

    @Override
    public synchronized boolean deleteConversation(Long workspaceId, Long conversationId) {
        ChatConversation conversation = conversations.get(conversationId);
        if (conversation == null || !conversation.workspaceId().equals(workspaceId)) {
            return false;
        }
        conversations.remove(conversationId);
        messages.entrySet().removeIf(entry -> belongsTo(entry.getValue(), workspaceId, conversationId));
        return true;
    }

    @Override
    public List<ChatConversation> findConversationsByWorkspaceId(Long workspaceId, int offset, int limit) {
        return conversations.values().stream()
                .filter(conversation -> conversation.workspaceId().equals(workspaceId))
                .sorted(Comparator.comparing(ChatConversation::updatedAt)
                        .reversed()
                        .thenComparing(ChatConversation::id, Comparator.reverseOrder()))
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public long countConversationsByWorkspaceId(Long workspaceId) {
        return conversations.values().stream()
                .filter(conversation -> conversation.workspaceId().equals(workspaceId))
                .count();
    }

    @Override
    public synchronized ChatMessage createMessage(
            Long workspaceId,
            Long conversationId,
            ChatMessageRole role,
            ChatMessageStatus status,
            String content
    ) {
        requireActiveConversation(workspaceId, conversationId);
        OffsetDateTime now = OffsetDateTime.now();
        ChatMessage message = new ChatMessage(
                messageIdGenerator.incrementAndGet(),
                workspaceId,
                conversationId,
                role,
                status,
                content,
                "",
                now,
                now
        );
        messages.put(message.id(), message);
        touchConversation(workspaceId, conversationId, now);
        return message;
    }

    @Override
    public Optional<ChatMessage> findMessageByWorkspaceIdAndConversationIdAndId(
            Long workspaceId,
            Long conversationId,
            Long messageId
    ) {
        return Optional.ofNullable(messages.get(messageId))
                .filter(message -> message.workspaceId().equals(workspaceId))
                .filter(message -> message.conversationId().equals(conversationId));
    }

    @Override
    public Optional<ChatMessage> transitionPendingMessage(
            Long workspaceId,
            Long conversationId,
            Long messageId,
            ChatMessageStatus targetStatus,
            String content,
            String failureReason
    ) {
        AtomicReference<ChatMessage> result = new AtomicReference<>();
        messages.computeIfPresent(messageId, (ignored, existing) -> {
            if (!belongsTo(existing, workspaceId, conversationId)) {
                return existing;
            }
            if (existing.status() != ChatMessageStatus.PENDING) {
                result.set(existing);
                return existing;
            }
            ChatMessage transitioned = new ChatMessage(
                    existing.id(),
                    existing.workspaceId(),
                    existing.conversationId(),
                    existing.role(),
                    targetStatus,
                    content,
                    failureReason,
                    existing.createdAt(),
                    OffsetDateTime.now()
            );
            result.set(transitioned);
            return transitioned;
        });
        ChatMessage transitioned = result.get();
        if (transitioned != null) {
            touchConversation(workspaceId, conversationId, transitioned.updatedAt());
        }
        return Optional.ofNullable(transitioned);
    }

    @Override
    public List<ChatMessage> findMessagesByWorkspaceIdAndConversationId(
            Long workspaceId,
            Long conversationId,
            int offset,
            int limit
    ) {
        requireConversation(workspaceId, conversationId);
        return messages.values().stream()
                .filter(message -> belongsTo(message, workspaceId, conversationId))
                .sorted(Comparator.comparing(ChatMessage::createdAt).thenComparing(ChatMessage::id))
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public long countMessagesByWorkspaceIdAndConversationId(Long workspaceId, Long conversationId) {
        requireConversation(workspaceId, conversationId);
        return messages.values().stream()
                .filter(message -> belongsTo(message, workspaceId, conversationId))
                .count();
    }

    @Override
    public List<ChatMessage> findRecentCompletedMessages(
            Long workspaceId,
            Long conversationId,
            int limit
    ) {
        requireConversation(workspaceId, conversationId);
        List<ChatMessage> newestFirst = messages.values().stream()
                .filter(message -> belongsTo(message, workspaceId, conversationId))
                .filter(message -> message.status() == ChatMessageStatus.COMPLETED)
                .sorted(Comparator.comparing(ChatMessage::createdAt)
                        .reversed()
                        .thenComparing(ChatMessage::id, Comparator.reverseOrder()))
                .limit(limit)
                .toList();
        List<ChatMessage> chronological = new ArrayList<>(newestFirst);
        chronological.sort(Comparator.comparing(ChatMessage::createdAt).thenComparing(ChatMessage::id));
        return List.copyOf(chronological);
    }

    private boolean belongsTo(ChatMessage message, Long workspaceId, Long conversationId) {
        return message.workspaceId().equals(workspaceId) && message.conversationId().equals(conversationId);
    }

    private void requireConversation(Long workspaceId, Long conversationId) {
        if (findConversationByWorkspaceIdAndId(workspaceId, conversationId).isEmpty()) {
            throw new IllegalStateException("会话不存在或不属于当前知识空间");
        }
    }

    private void requireActiveConversation(Long workspaceId, Long conversationId) {
        ChatConversation conversation = findConversationByWorkspaceIdAndId(workspaceId, conversationId)
                .orElseThrow(() -> new IllegalStateException("会话不存在或不属于当前知识空间"));
        if (conversation.status() == ChatConversationStatus.ARCHIVED) {
            throw new IllegalStateException("归档会话不能创建新消息");
        }
    }

    private void touchConversation(Long workspaceId, Long conversationId, OffsetDateTime updatedAt) {
        conversations.computeIfPresent(conversationId, (ignored, conversation) -> {
            if (!conversation.workspaceId().equals(workspaceId)) {
                return conversation;
            }
            return new ChatConversation(
                    conversation.id(),
                    conversation.workspaceId(),
                    conversation.title(),
                    conversation.status(),
                    conversation.createdAt(),
                    updatedAt
            );
        });
    }
}
