package com.agentmind.knowledge.model.dto;

import java.util.List;

/**
 * 开发和前端联调用的语义检索响应。
 */
public record KnowledgeSearchResponse(
        String query,
        int topK,
        List<KnowledgeSearchResultResponse> results
) {
}
