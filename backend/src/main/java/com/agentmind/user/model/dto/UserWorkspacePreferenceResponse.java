package com.agentmind.user.model.dto;

import com.agentmind.user.model.CitationPolicy;
import java.time.OffsetDateTime;

/** 设置页读取和保存后使用的用户知识空间偏好响应。 */
public record UserWorkspacePreferenceResponse(
        Long workspaceId,
        String chatModel,
        String embeddingModel,
        CitationPolicy citationPolicy,
        int defaultTopK,
        long version,
        boolean persisted,
        OffsetDateTime updatedAt
) {
}
