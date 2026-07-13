package com.agentmind.chat.memory.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.chat.memory.config.ChatMemoryProperties;
import com.agentmind.chat.memory.model.ChatMessage;
import com.agentmind.chat.memory.model.ChatMessageRole;
import com.agentmind.chat.memory.model.ChatMessageStatus;
import com.agentmind.chat.memory.repository.InMemoryChatMemoryRepository;
import org.junit.jupiter.api.Test;

class ChatMemoryServiceTests {

    private final ChatMemoryProperties properties = new ChatMemoryProperties();
    private final InMemoryChatMemoryRepository repository = new InMemoryChatMemoryRepository();
    private final ChatMemoryService service = new ChatMemoryService(repository, properties);

    @Test
    void beginTurnShouldLimitHistoryByMessageCount() {
        properties.setMaxMessages(2);
        properties.setMaxContextChars(1_000);

        ChatTurnContext firstTurn = service.beginTurn(1L, null, "旧问题");
        service.completeAssistant(1L, firstTurn.conversation().id(), firstTurn.assistantMessage().id(), "旧回答");
        ChatTurnContext secondTurn = service.beginTurn(1L, firstTurn.conversation().id(), "新问题");
        service.completeAssistant(1L, secondTurn.conversation().id(), secondTurn.assistantMessage().id(), "新回答");

        ChatTurnContext thirdTurn = service.beginTurn(1L, firstTurn.conversation().id(), "继续追问");

        assertThat(thirdTurn.history()).containsExactly(
                new ChatMemoryEntry(ChatMessageRole.USER, "新问题"),
                new ChatMemoryEntry(ChatMessageRole.ASSISTANT, "新回答")
        );
    }

    @Test
    void beginTurnShouldLimitHistoryByContextCharacters() {
        properties.setMaxMessages(10);
        properties.setMaxContextChars(5);
        ChatTurnContext firstTurn = service.beginTurn(1L, null, "旧问题");
        service.completeAssistant(1L, firstTurn.conversation().id(), firstTurn.assistantMessage().id(), "123456789");

        ChatTurnContext secondTurn = service.beginTurn(1L, firstTurn.conversation().id(), "新问题");

        assertThat(secondTurn.history()).containsExactly(
                new ChatMemoryEntry(ChatMessageRole.ASSISTANT, "12345")
        );
    }

    @Test
    void failedAndCancelledAssistantMessagesShouldRemainEmptyAndStayOutOfHistory() {
        ChatTurnContext failedTurn = service.beginTurn(1L, null, "失败问题");
        service.failAssistant(
                1L,
                failedTurn.conversation().id(),
                failedTurn.assistantMessage().id(),
                "模型调用失败"
        );
        ChatTurnContext cancelledTurn = service.beginTurn(1L, failedTurn.conversation().id(), "取消问题");
        service.cancelAssistant(
                1L,
                cancelledTurn.conversation().id(),
                cancelledTurn.assistantMessage().id(),
                "客户端断开"
        );

        ChatTurnContext nextTurn = service.beginTurn(1L, failedTurn.conversation().id(), "后续问题");
        ChatMessage failedMessage = repository.findMessageByWorkspaceIdAndConversationIdAndId(
                1L,
                failedTurn.conversation().id(),
                failedTurn.assistantMessage().id()
        ).orElseThrow();
        ChatMessage cancelledMessage = repository.findMessageByWorkspaceIdAndConversationIdAndId(
                1L,
                failedTurn.conversation().id(),
                cancelledTurn.assistantMessage().id()
        ).orElseThrow();

        assertThat(failedMessage.status()).isEqualTo(ChatMessageStatus.FAILED);
        assertThat(failedMessage.content()).isEmpty();
        assertThat(cancelledMessage.status()).isEqualTo(ChatMessageStatus.CANCELLED);
        assertThat(cancelledMessage.content()).isEmpty();
        assertThat(nextTurn.history())
                .extracting(ChatMemoryEntry::content)
                .contains("失败问题", "取消问题")
                .doesNotContain("模型调用失败", "客户端断开");
    }

    @Test
    void completedAssistantMessageShouldNotBeOverwrittenByLateCancellation() {
        ChatTurnContext turn = service.beginTurn(1L, null, "正常问题");
        service.completeAssistant(1L, turn.conversation().id(), turn.assistantMessage().id(), "完整回答");
        service.cancelAssistant(1L, turn.conversation().id(), turn.assistantMessage().id(), "迟到的断连信号");

        ChatMessage assistantMessage = repository.findMessageByWorkspaceIdAndConversationIdAndId(
                1L,
                turn.conversation().id(),
                turn.assistantMessage().id()
        ).orElseThrow();
        assertThat(assistantMessage.status()).isEqualTo(ChatMessageStatus.COMPLETED);
        assertThat(assistantMessage.content()).isEqualTo("完整回答");
    }
}
