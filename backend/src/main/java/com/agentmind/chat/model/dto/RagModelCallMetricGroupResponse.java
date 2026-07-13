package com.agentmind.chat.model.dto;

import java.math.BigDecimal;

/**
 * 单个模型与提示词版本组合的调用指标。
 *
 * <p>成功率和降级率使用 0 到 1 之间的小数表示，平均耗时单位为毫秒。
 * 前端可以直接把比率格式化为百分比，也可以比较不同组合的稳定性和响应速度。</p>
 */
public record RagModelCallMetricGroupResponse(
        String modelName,
        String promptVersion,
        long totalCallCount,
        long successfulCallCount,
        long fallbackCallCount,
        long failedCallCount,
        long cancelledCallCount,
        BigDecimal successRate,
        BigDecimal fallbackRate,
        BigDecimal averageElapsedMillis
) {
}
