package com.agentmind.user.model;

import java.time.OffsetDateTime;

/**
 * 用户在单个知识空间内的人工智能使用偏好。
 *
 * <p>这里只保存模型标识和回答策略，不保存 API Key、访问令牌或供应商端点等敏感配置。</p>
 */
public record UserWorkspacePreference(
        Long userId,
        Long workspaceId,
        String chatModel,
        String embeddingModel,
        CitationPolicy citationPolicy,
        int defaultTopK,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
