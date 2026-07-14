package com.agentmind.chat.memory.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentmind.chat.memory.model.ChatConversation;
import com.agentmind.chat.memory.model.ChatConversationStatus;
import com.agentmind.chat.memory.model.ChatMessage;
import com.agentmind.chat.memory.model.ChatMessageRole;
import com.agentmind.chat.memory.model.ChatMessageStatus;
import org.junit.jupiter.api.Test;

/**
 * 会话记忆仓储的公共契约测试。
 *
 * <p>内存和 Redis 测试类继承同一组断言，确保两种存储在重命名、归档、终态更新、删除和
 * 知识空间隔离方面具有一致语义。新增适配器时也应复用该契约。</p>
 */
public abstract class ChatMemoryRepositoryContractTests {

    protected abstract ChatMemoryRepository repository();

    @Test
    void renameAndArchiveShouldPreserveWorkspaceBoundaryAndRemainIdempotent() {
        ChatConversation conversation = repository().createConversation(100L, "原始标题");

        ChatConversation renamed = repository().renameConversation(100L, conversation.id(), "新标题")
                .orElseThrow();
        ChatConversation archived = repository().archiveConversation(100L, conversation.id())
                .orElseThrow();
        ChatConversation archivedAgain = repository().archiveConversation(100L, conversation.id())
                .orElseThrow();

        assertThat(renamed.title()).isEqualTo("新标题");
        assertThat(archived.status()).isEqualTo(ChatConversationStatus.ARCHIVED);
        assertThat(archivedAgain).isEqualTo(archived);
        assertThat(repository().renameConversation(200L, conversation.id(), "越权标题")).isEmpty();
        assertThat(repository().archiveConversation(200L, conversation.id())).isEmpty();
        assertThat(repository().deleteConversation(200L, conversation.id())).isFalse();
    }

    @Test
    void archiveShouldBlockNewMessagesButAllowPendingAssistantToFinish() {
        ChatConversation conversation = repository().createConversation(100L, "归档语义");
        ChatMessage pendingAssistant = repository().createMessage(
                100L,
                conversation.id(),
                ChatMessageRole.ASSISTANT,
                ChatMessageStatus.PENDING,
                ""
        );
        repository().archiveConversation(100L, conversation.id());

        ChatMessage completedAssistant = repository().transitionPendingMessage(
                100L,
                conversation.id(),
                pendingAssistant.id(),
                ChatMessageStatus.COMPLETED,
                "归档前已经开始的完整回答",
                ""
        ).orElseThrow();

        assertThat(completedAssistant.status()).isEqualTo(ChatMessageStatus.COMPLETED);
        assertThatThrownBy(() -> repository().createMessage(
                100L,
                conversation.id(),
                ChatMessageRole.USER,
                ChatMessageStatus.COMPLETED,
                "归档后新消息"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("归档会话不能创建新消息");
    }

    @Test
    void deleteShouldRemoveConversationMessagesAndIndexMembership() {
        ChatConversation conversation = repository().createConversation(100L, "待删除会话");
        ChatMessage message = repository().createMessage(
                100L,
                conversation.id(),
                ChatMessageRole.USER,
                ChatMessageStatus.COMPLETED,
                "待删除消息"
        );

        assertThat(repository().deleteConversation(100L, conversation.id())).isTrue();

        assertThat(repository().findConversationByWorkspaceIdAndId(100L, conversation.id())).isEmpty();
        assertThat(repository().findMessageByWorkspaceIdAndConversationIdAndId(
                100L,
                conversation.id(),
                message.id()
        )).isEmpty();
        assertThat(repository().findConversationsByWorkspaceId(100L, 0, 20)).isEmpty();
        assertThat(repository().countConversationsByWorkspaceId(100L)).isZero();
        assertThat(repository().deleteConversation(100L, conversation.id())).isFalse();
    }
}
