package com.agentmind.study.memory.model;

import java.time.OffsetDateTime;
import java.util.List;

/** 从短期会话压缩得到的长期学习摘要。 */
public record ConversationLearningSummary(
        Long id,
        Long ownerUserId,
        Long workspaceId,
        Long conversationId,
        String summary,
        List<String> topics,
        List<String> weakTopics,
        int messageCount,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public ConversationLearningSummary {
        topics = List.copyOf(topics);
        weakTopics = List.copyOf(weakTopics);
    }

    public ConversationLearningSummary withId(Long generatedId) {
        return new ConversationLearningSummary(
                generatedId, ownerUserId, workspaceId, conversationId, summary,
                topics, weakTopics, messageCount, version, createdAt, updatedAt
        );
    }
}
