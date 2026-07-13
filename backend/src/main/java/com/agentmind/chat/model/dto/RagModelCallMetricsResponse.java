package com.agentmind.chat.model.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 指定知识空间的模型调用聚合指标响应。
 *
 * <p>响应同时提供知识空间总体指标和分组明细。总体指标适合评估面板顶部概览，
 * 分组明细用于比较不同模型与提示词版本组合的效果。</p>
 */
public record RagModelCallMetricsResponse(
        Long workspaceId,
        long totalCallCount,
        long successfulCallCount,
        long fallbackCallCount,
        long failedCallCount,
        BigDecimal successRate,
        BigDecimal fallbackRate,
        BigDecimal averageElapsedMillis,
        List<RagModelCallMetricGroupResponse> groups
) {
}
