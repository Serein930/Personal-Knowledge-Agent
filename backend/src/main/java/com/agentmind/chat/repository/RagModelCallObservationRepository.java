package com.agentmind.chat.repository;

import com.agentmind.chat.model.RagModelCallObservation;
import com.agentmind.chat.model.RagModelCallMetricAggregate;
import com.agentmind.chat.model.RagModelCallStatus;
import java.util.List;

/**
 * 模型调用观测记录仓储端口。
 *
 * <p>应用服务只依赖该接口。默认内存实现用于本地开发，数据库实现用于本地关系数据库联调，
 * 两种实现必须保持相同的知识空间过滤、状态过滤和分页语义。</p>
 */
public interface RagModelCallObservationRepository {

    void save(RagModelCallObservation observation);

    List<RagModelCallObservation> findByWorkspaceId(
            Long workspaceId,
            RagModelCallStatus status,
            int offset,
            int limit
    );

    long countByWorkspaceId(Long workspaceId, RagModelCallStatus status);

    /**
     * 按模型名称和提示词版本聚合指定知识空间内的最终调用记录。
     *
     * <p>聚合必须在存储适配器内部完成，避免数据库模式下把全部审计明细加载到应用内存。</p>
     */
    List<RagModelCallMetricAggregate> aggregateMetricsByWorkspaceId(Long workspaceId);
}
