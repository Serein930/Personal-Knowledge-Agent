package com.agentmind.chat.service;

import com.agentmind.chat.model.RagModelCallMetricAggregate;
import com.agentmind.chat.model.dto.RagModelCallMetricGroupResponse;
import com.agentmind.chat.model.dto.RagModelCallMetricsResponse;
import com.agentmind.chat.repository.RagModelCallObservationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 模型调用聚合指标应用服务。
 *
 * <p>仓储只负责产生无损的计数和耗时总和，本服务统一计算比率、平均值和总体指标。
 * 这样内存模式与数据库模式能够向前端返回完全一致的指标语义。</p>
 */
@Service
public class RagModelCallMetricService {

    private static final int RATE_SCALE = 4;
    private static final int ELAPSED_SCALE = 2;

    private final RagModelCallObservationRepository repository;

    public RagModelCallMetricService(RagModelCallObservationRepository repository) {
        this.repository = repository;
    }

    public RagModelCallMetricsResponse getMetrics(Long workspaceId) {
        List<RagModelCallMetricAggregate> aggregates = repository.aggregateMetricsByWorkspaceId(workspaceId);
        List<RagModelCallMetricGroupResponse> groups = aggregates.stream()
                .map(this::toGroupResponse)
                .toList();

        long totalCallCount = aggregates.stream()
                .mapToLong(RagModelCallMetricAggregate::totalCallCount)
                .sum();
        long successfulCallCount = aggregates.stream()
                .mapToLong(RagModelCallMetricAggregate::successfulCallCount)
                .sum();
        long fallbackCallCount = aggregates.stream()
                .mapToLong(RagModelCallMetricAggregate::fallbackCallCount)
                .sum();
        long failedCallCount = aggregates.stream()
                .mapToLong(RagModelCallMetricAggregate::failedCallCount)
                .sum();
        long totalElapsedMillis = aggregates.stream()
                .mapToLong(RagModelCallMetricAggregate::totalElapsedMillis)
                .sum();

        return new RagModelCallMetricsResponse(
                workspaceId,
                totalCallCount,
                successfulCallCount,
                fallbackCallCount,
                failedCallCount,
                calculateRate(successfulCallCount, totalCallCount),
                calculateRate(fallbackCallCount, totalCallCount),
                calculateAverage(totalElapsedMillis, totalCallCount),
                groups
        );
    }

    private RagModelCallMetricGroupResponse toGroupResponse(RagModelCallMetricAggregate aggregate) {
        return new RagModelCallMetricGroupResponse(
                aggregate.modelName(),
                aggregate.promptVersion(),
                aggregate.totalCallCount(),
                aggregate.successfulCallCount(),
                aggregate.fallbackCallCount(),
                aggregate.failedCallCount(),
                calculateRate(aggregate.successfulCallCount(), aggregate.totalCallCount()),
                calculateRate(aggregate.fallbackCallCount(), aggregate.totalCallCount()),
                calculateAverage(aggregate.totalElapsedMillis(), aggregate.totalCallCount())
        );
    }

    private BigDecimal calculateRate(long count, long total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE);
        }
        return BigDecimal.valueOf(count)
                .divide(BigDecimal.valueOf(total), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateAverage(long totalElapsedMillis, long totalCallCount) {
        if (totalCallCount == 0) {
            return BigDecimal.ZERO.setScale(ELAPSED_SCALE);
        }
        return BigDecimal.valueOf(totalElapsedMillis)
                .divide(BigDecimal.valueOf(totalCallCount), ELAPSED_SCALE, RoundingMode.HALF_UP);
    }

}
