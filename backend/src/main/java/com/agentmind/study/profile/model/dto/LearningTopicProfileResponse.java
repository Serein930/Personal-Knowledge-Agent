package com.agentmind.study.profile.model.dto;

import com.agentmind.study.profile.model.LearningTopicLevel;
import java.time.OffsetDateTime;

/** 主题学习画像响应。 */
public record LearningTopicProfileResponse(
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
