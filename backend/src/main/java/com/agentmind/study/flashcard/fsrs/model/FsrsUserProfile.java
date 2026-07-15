package com.agentmind.study.flashcard.fsrs.model;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 用户级 FSRS 参数档案。
 *
 * <p>档案属于用户而不是单一知识空间，使同一用户在不同知识空间获得一致的记忆模型。
 * 参数列表保存 FSRS 权重，期望保持率控制复习强度，版本号用于审计参数变化。</p>
 */
public record FsrsUserProfile(
        Long ownerUserId,
        List<Double> parameters,
        double desiredRetention,
        long version,
        FsrsUserProfileSource source,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public FsrsUserProfile {
        parameters = List.copyOf(parameters);
    }
}
