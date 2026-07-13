package com.agentmind.chat.repository;

import com.agentmind.chat.model.RagModelCallObservation;
import com.agentmind.chat.model.RagModelCallMetricAggregate;
import com.agentmind.chat.model.RagModelCallStatus;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * 模型调用观测记录的内存仓库。
 *
 * <p>写入使用写时复制列表，适合当前开发阶段的小数据量并发联调。服务重启后数据会清空，
 * 因此它不用于生产审计，也不替代数据库适配器。</p>
 */
@Repository
@ConditionalOnProperty(
        prefix = "agentmind.rag",
        name = "observation-store",
        havingValue = "memory",
        matchIfMissing = true
)
public class InMemoryRagModelCallObservationRepository implements RagModelCallObservationRepository {

    private final CopyOnWriteArrayList<RagModelCallObservation> observations = new CopyOnWriteArrayList<>();

    @Override
    public void save(RagModelCallObservation observation) {
        observations.add(observation);
    }

    @Override
    public List<RagModelCallObservation> findByWorkspaceId(
            Long workspaceId,
            RagModelCallStatus status,
            int offset,
            int limit
    ) {
        return observations.stream()
                .filter(observation -> observation.workspaceId().equals(workspaceId))
                .filter(observation -> status == null || observation.status() == status)
                .sorted(Comparator.comparing(RagModelCallObservation::createdAt).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public long countByWorkspaceId(Long workspaceId, RagModelCallStatus status) {
        return observations.stream()
                .filter(observation -> observation.workspaceId().equals(workspaceId))
                .filter(observation -> status == null || observation.status() == status)
                .count();
    }

    @Override
    public List<RagModelCallMetricAggregate> aggregateMetricsByWorkspaceId(Long workspaceId) {
        Map<MetricGroupKey, MetricAccumulator> accumulators = new HashMap<>();
        observations.stream()
                .filter(observation -> observation.workspaceId().equals(workspaceId))
                .forEach(observation -> accumulators
                        .computeIfAbsent(
                                new MetricGroupKey(observation.modelName(), observation.promptVersion()),
                                ignored -> new MetricAccumulator()
                        )
                        .add(observation));

        return accumulators.entrySet().stream()
                .map(entry -> entry.getValue().toAggregate(entry.getKey()))
                .sorted(Comparator.comparingLong(RagModelCallMetricAggregate::totalCallCount)
                        .reversed()
                        .thenComparing(RagModelCallMetricAggregate::modelName)
                        .thenComparing(RagModelCallMetricAggregate::promptVersion))
                .toList();
    }

    /**
     * 内存聚合使用的复合分组键，确保相同模型的不同提示词版本分别统计。
     */
    private record MetricGroupKey(String modelName, String promptVersion) {
    }

    /**
     * 单次遍历累加指标，避免为每个分组重复扫描全部观测记录。
     */
    private static final class MetricAccumulator {

        private long totalCallCount;
        private long successfulCallCount;
        private long fallbackCallCount;
        private long failedCallCount;
        private long totalElapsedMillis;

        private void add(RagModelCallObservation observation) {
            totalCallCount++;
            totalElapsedMillis += observation.elapsedMillis();
            if (observation.status() == RagModelCallStatus.SUCCEEDED) {
                successfulCallCount++;
            } else if (observation.status() == RagModelCallStatus.FALLBACK) {
                fallbackCallCount++;
            } else if (observation.status() == RagModelCallStatus.FAILED) {
                failedCallCount++;
            }
        }

        private RagModelCallMetricAggregate toAggregate(MetricGroupKey key) {
            return new RagModelCallMetricAggregate(
                    key.modelName(),
                    key.promptVersion(),
                    totalCallCount,
                    successfulCallCount,
                    fallbackCallCount,
                    failedCallCount,
                    totalElapsedMillis
            );
        }
    }
}
