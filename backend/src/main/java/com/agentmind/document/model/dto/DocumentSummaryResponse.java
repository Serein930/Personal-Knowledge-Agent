package com.agentmind.document.model.dto;

import com.agentmind.document.model.DocumentSourceType;
import com.agentmind.document.model.IngestionStatus;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 文档列表项响应 DTO。
 *
 * <p>该结构对齐前端知识库表格。它只暴露列表展示需要的信息，不暴露原始文件路径、
 * password、内部存储桶等敏感或实现细节字段。</p>
 */
public record DocumentSummaryResponse(
        Long id,
        String title,
        DocumentSourceType sourceType,
        Long workspaceId,
        String workspaceName,
        List<String> tags,
        IngestionStatus ingestionStatus,
        int chunkCount,
        OffsetDateTime updatedAt
) {
}
