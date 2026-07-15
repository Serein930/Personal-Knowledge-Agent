package com.agentmind.study.memory.model.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** 长期会话学习摘要响应。 */
public record ConversationLearningSummaryResponse(
        Long id,
        Long conversationId,
        String summary,
        List<String> topics,
        List<String> weakTopics,
        int messageCount,
        long version,
        OffsetDateTime updatedAt
) {
}
