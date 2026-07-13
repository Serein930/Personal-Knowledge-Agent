package com.agentmind.chat.memory.service;

import com.agentmind.chat.memory.model.ChatMessageRole;

/**
 * 放入模型提示词的短期记忆条目。
 *
 * <p>该结构只保留角色和经过窗口裁剪的正文，不暴露消息状态或持久化细节。</p>
 */
public record ChatMemoryEntry(
        ChatMessageRole role,
        String content
) {
}
