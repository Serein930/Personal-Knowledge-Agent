package com.agentmind.chat.model;

/**
 * 模型调用指标的仓储层聚合结果。
 *
 * <p>仓储实现按“模型名称 + 提示词版本”分组，并返回原始计数与总耗时。
 * 成功率、降级率和平均耗时统一由应用服务计算，避免不同存储实现产生不同的舍入结果。</p>
 */
public record RagModelCallMetricAggregate(
        String modelName,
        String promptVersion,
        long totalCallCount,
        long successfulCallCount,
        long fallbackCallCount,
        long failedCallCount,
        long cancelledCallCount,
        long totalElapsedMillis
) {
}
