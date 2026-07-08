package com.agentmind.document.model.dto;

import com.agentmind.document.model.DocumentSourceType;
import com.agentmind.document.model.IngestionStatus;

/**
 * 文档列表查询条件。
 *
 * <p>该 DTO 对应文档列表接口的查询参数。当前阶段不实现 Controller，
 * 但先稳定字段，便于前端和后续查询服务围绕同一契约开发。</p>
 */
public record DocumentQueryRequest(
        int page,
        int pageSize,
        String keyword,
        DocumentSourceType sourceType,
        IngestionStatus status,
        String tag
) {

    /**
     * 生成默认查询条件，供测试和后续 Controller 兜底使用。
     */
    public static DocumentQueryRequest defaults() {
        return new DocumentQueryRequest(1, 20, null, null, null, null);
    }
}
