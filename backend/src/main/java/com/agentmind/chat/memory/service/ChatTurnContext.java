package com.agentmind.chat.memory.service;

import com.agentmind.chat.memory.model.ChatConversation;
import com.agentmind.chat.memory.model.ChatMessage;
import java.util.List;

/**
 * 一轮问答开始后的短期记忆上下文。
 *
 * <p>历史窗口只包含本轮开始前已经完成的消息；当前用户消息和助手等待消息单独返回，
 * 防止当前问题在提示词中重复出现。</p>
 */
public record ChatTurnContext(
        ChatConversation conversation,
        ChatMessage userMessage,
        ChatMessage assistantMessage,
        List<ChatMemoryEntry> history
) {
}
