package com.agentmind.chat.model.dto;

import java.time.OffsetDateTime;

/**
 * 流式问答开始时发送的元数据事件。
 */
public record RagStreamMetadataEvent(
        Long conversationId,
        Long messageId,
        String promptVersion,
        String answerGenerator,
        String modelName,
        int citationCount,
        OffsetDateTime startedAt
) {
}
