package com.agentmind.chat.service;

import com.agentmind.chat.model.RagModelCallObservation;
import com.agentmind.chat.model.RagModelCallStatus;
import com.agentmind.chat.model.dto.RagModelCallObservationResponse;
import com.agentmind.chat.repository.RagModelCallObservationRepository;
import com.agentmind.common.response.PageResponse;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 模型调用观测记录查询服务。
 *
 * <p>该服务统一分页计算、知识空间过滤和领域记录到响应对象的转换，
 * 控制层不直接访问内存或数据库仓库。</p>
 */
@Service
public class RagModelCallAuditService {

    private final RagModelCallObservationRepository repository;

    public RagModelCallAuditService(RagModelCallObservationRepository repository) {
        this.repository = repository;
    }

    public PageResponse<RagModelCallObservationResponse> listObservations(
            Long workspaceId,
            int page,
            int pageSize,
            RagModelCallStatus status
    ) {
        int offset = Math.multiplyExact(page - 1, pageSize);
        List<RagModelCallObservationResponse> records = repository
                .findByWorkspaceId(workspaceId, status, offset, pageSize)
                .stream()
                .map(this::toResponse)
                .toList();
        long total = repository.countByWorkspaceId(workspaceId, status);
        return new PageResponse<>(records, page, pageSize, total);
    }

    private RagModelCallObservationResponse toResponse(RagModelCallObservation observation) {
        return new RagModelCallObservationResponse(
                observation.id(),
                observation.promptVersion(),
                observation.answerGenerator(),
                observation.modelName(),
                observation.citationCount(),
                observation.refused(),
                observation.status(),
                observation.elapsedMillis(),
                observation.answerLength(),
                observation.failureReason(),
                observation.createdAt()
        );
    }
}
