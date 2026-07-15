package com.agentmind.study.flashcard.fsrs.model.dto;

import com.agentmind.study.flashcard.fsrs.model.FsrsUserProfileSource;
import java.time.OffsetDateTime;
import java.util.List;

/** FSRS 参数历史版本响应。 */
public record FsrsProfileVersionResponse(
        long version,
        List<Double> parameters,
        double desiredRetention,
        FsrsUserProfileSource source,
        String changeReason,
        OffsetDateTime createdAt
) {
}
