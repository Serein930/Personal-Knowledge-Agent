package com.agentmind.workspace.model.dto;

import com.agentmind.workspace.model.WorkspaceVisibility;
import java.time.OffsetDateTime;

/** 知识空间安全响应视图。 */
public record WorkspaceResponse(
        Long id,
        String name,
        String description,
        WorkspaceVisibility visibility,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
