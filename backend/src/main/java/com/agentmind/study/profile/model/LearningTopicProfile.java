package com.agentmind.study.profile.model;

import java.time.OffsetDateTime;

/** 用户在一个知识空间内的主题学习画像快照。 */
public record LearningTopicProfile(
        Long ownerUserId,
        Long workspaceId,
        String topic,
        int cardCount,
        int reviewCount,
        double successRate,
        double lapseRate,
        double masteryScore,
        LearningTopicLevel level,
        OffsetDateTime lastReviewedAt,
        OffsetDateTime updatedAt
) {
}
