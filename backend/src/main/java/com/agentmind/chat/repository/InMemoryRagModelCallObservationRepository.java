package com.agentmind.chat.repository;

import com.agentmind.chat.model.RagModelCallObservation;
import com.agentmind.chat.model.RagModelCallStatus;
import java.util.Comparator;
import java.util.List;
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
}
