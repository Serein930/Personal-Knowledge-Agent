package com.agentmind.common.response;

import java.util.List;

/**
 * 统一分页响应结构。
 *
 * <p>第一阶段先定义契约，后续文档列表、任务列表、审计记录列表都复用该结构。</p>
 */
public record PageResponse<T>(
        List<T> records,
        int page,
        int pageSize,
        long total
) {
}
