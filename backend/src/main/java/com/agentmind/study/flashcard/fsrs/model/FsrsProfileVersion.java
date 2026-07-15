package com.agentmind.study.flashcard.fsrs.model;

import java.time.OffsetDateTime;
import java.util.List;

/** 用户 FSRS 参数的一份不可变历史版本。 */
public record FsrsProfileVersion(
        Long ownerUserId,
        long version,
        List<Double> parameters,
        double desiredRetention,
        FsrsUserProfileSource source,
        String changeReason,
        OffsetDateTime createdAt
) {

    public FsrsProfileVersion {
        parameters = List.copyOf(parameters);
    }
}
