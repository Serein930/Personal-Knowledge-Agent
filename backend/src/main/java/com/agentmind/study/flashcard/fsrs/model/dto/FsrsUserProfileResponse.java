package com.agentmind.study.flashcard.fsrs.model.dto;

import com.agentmind.study.flashcard.fsrs.model.FsrsUserProfileSource;
import java.time.OffsetDateTime;
import java.util.List;

/** 用户级 FSRS 参数响应。 */
public record FsrsUserProfileResponse(
        List<Double> parameters,
        double desiredRetention,
        long version,
        FsrsUserProfileSource source,
        OffsetDateTime updatedAt
) {
}
