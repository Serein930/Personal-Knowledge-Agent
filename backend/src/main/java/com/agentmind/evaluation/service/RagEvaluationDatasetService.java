package com.agentmind.evaluation.service;

import com.agentmind.agent.service.AgentToolExecutionAuthorizer;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.common.response.PageResponse;
import com.agentmind.evaluation.config.RagEvaluationProperties;
import com.agentmind.evaluation.model.RagEvaluationCase;
import com.agentmind.evaluation.model.RagEvaluationDataset;
import com.agentmind.evaluation.model.RagEvaluationDatasetVersion;
import com.agentmind.evaluation.model.dto.CreateRagEvaluationDatasetRequest;
import com.agentmind.evaluation.model.dto.CreateRagEvaluationDatasetVersionRequest;
import com.agentmind.evaluation.model.dto.RagEvaluationCaseRequest;
import com.agentmind.evaluation.model.dto.RagEvaluationDatasetResponse;
import com.agentmind.evaluation.model.dto.RagEvaluationDatasetVersionResponse;
import com.agentmind.evaluation.repository.RagEvaluationDatasetRepository;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 固定评估集应用服务。
 *
 * <p>评估集主记录只保存名称和最新版本号；具体题目进入不可变版本。任何题目修订都创建新版本，
 * 从而保证历史评估任务始终可复现，而不会被后续编辑悄悄改变。</p>
 */
@Service
public class RagEvaluationDatasetService {

    private final RagEvaluationDatasetRepository repository;
    private final AgentToolExecutionAuthorizer authorizer;
    private final RagEvaluationProperties properties;

    public RagEvaluationDatasetService(
            RagEvaluationDatasetRepository repository,
            AgentToolExecutionAuthorizer authorizer,
            RagEvaluationProperties properties
    ) {
        this.repository = repository;
        this.authorizer = authorizer;
        this.properties = properties;
    }

    @Transactional
    public RagEvaluationDatasetVersionResponse create(
            AgentToolExecutionContext context,
            CreateRagEvaluationDatasetRequest request
    ) {
        authorize(context);
        String name = request.name().trim();
        if (repository.existsByScopeAndName(context.ownerUserId(), context.workspaceId(), name)) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "当前知识空间已存在同名评估集");
        }
        List<RagEvaluationCase> cases = normalizeCases(request.cases());
        OffsetDateTime now = OffsetDateTime.now();
        RagEvaluationDataset dataset = repository.saveDataset(new RagEvaluationDataset(
                null, context.ownerUserId(), context.workspaceId(), name, safe(request.description()),
                0, now, now
        ));
        return toVersionResponse(repository.saveVersion(new RagEvaluationDatasetVersion(
                null, dataset.id(), context.ownerUserId(), context.workspaceId(), 1,
                "创建首个固定版本", cases, now
        )));
    }

    @Transactional
    public RagEvaluationDatasetVersionResponse createVersion(
            AgentToolExecutionContext context,
            Long datasetId,
            CreateRagEvaluationDatasetVersionRequest request
    ) {
        authorize(context);
        RagEvaluationDataset dataset = requireDataset(context, datasetId);
        List<RagEvaluationCase> cases = normalizeCases(request.cases());
        RagEvaluationDatasetVersion saved = repository.saveVersion(new RagEvaluationDatasetVersion(
                null, dataset.id(), context.ownerUserId(), context.workspaceId(), dataset.latestVersion() + 1,
                safe(request.changeNote()), cases, OffsetDateTime.now()
        ));
        return toVersionResponse(saved);
    }

    public PageResponse<RagEvaluationDatasetResponse> list(
            AgentToolExecutionContext context,
            int page,
            int pageSize
    ) {
        authorize(context);
        int offset = (page - 1) * pageSize;
        List<RagEvaluationDatasetResponse> records = repository.findDatasetsByScope(
                context.ownerUserId(), context.workspaceId(), offset, pageSize
        ).stream().map(this::toDatasetResponse).toList();
        return new PageResponse<>(records, page, pageSize,
                repository.countDatasetsByScope(context.ownerUserId(), context.workspaceId()));
    }

    public List<RagEvaluationDatasetVersionResponse> listVersions(
            AgentToolExecutionContext context,
            Long datasetId
    ) {
        authorize(context);
        requireDataset(context, datasetId);
        return repository.findVersionsByScopeAndDatasetId(
                context.ownerUserId(), context.workspaceId(), datasetId
        ).stream().map(this::toVersionResponse).toList();
    }

    public RagEvaluationDatasetVersionResponse getVersion(
            AgentToolExecutionContext context,
            Long datasetId,
            int version
    ) {
        authorize(context);
        return toVersionResponse(requireVersion(context, datasetId, version));
    }

    RagEvaluationDatasetVersion requireVersion(
            AgentToolExecutionContext context,
            Long datasetId,
            int version
    ) {
        return repository.findVersionByScopeAndDatasetIdAndVersion(
                context.ownerUserId(), context.workspaceId(), datasetId, version
        ).orElseThrow(() -> new BusinessException(
                ErrorCode.RESOURCE_NOT_FOUND, "评估集版本不存在或无权访问"
        ));
    }

    RagEvaluationDataset requireDataset(AgentToolExecutionContext context, Long datasetId) {
        return repository.findDatasetByScopeAndId(context.ownerUserId(), context.workspaceId(), datasetId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "评估集不存在或无权访问"
                ));
    }

    private List<RagEvaluationCase> normalizeCases(List<RagEvaluationCaseRequest> requests) {
        if (requests.size() > properties.getMaximumCasesPerVersion()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "单个评估集版本最多允许" + properties.getMaximumCasesPerVersion() + "道题");
        }
        Set<String> caseKeys = new LinkedHashSet<>();
        return requests.stream().map(request -> {
            String caseKey = request.caseKey().trim();
            if (!caseKeys.add(caseKey.toLowerCase(Locale.ROOT))) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "评估题目标识不能重复：" + caseKey);
            }
            List<String> chunkIds = distinctStrings(request.expectedRelevantChunkIds());
            List<Long> documentIds = distinctPositiveLongs(request.expectedRelevantDocumentIds());
            if (!request.expectedRefusal() && chunkIds.isEmpty() && documentIds.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "可回答题必须配置期望片段编号或期望文档编号：" + caseKey);
            }
            return new RagEvaluationCase(
                    caseKey, request.question().trim(), chunkIds, documentIds,
                    request.expectedRefusal(), distinctStrings(request.expectedAnswerKeywords())
            );
        }).toList();
    }

    private List<String> distinctStrings(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim)
                .distinct().toList();
    }

    private List<Long> distinctPositiveLongs(List<Long> values) {
        if (values == null) {
            return List.of();
        }
        if (values.stream().anyMatch(value -> value == null || value <= 0)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "期望文档编号必须为正数");
        }
        return values.stream().distinct().toList();
    }

    private void authorize(AgentToolExecutionContext context) {
        authorizer.authorize(context);
    }

    private RagEvaluationDatasetResponse toDatasetResponse(RagEvaluationDataset dataset) {
        return new RagEvaluationDatasetResponse(
                dataset.id(), dataset.name(), dataset.description(), dataset.latestVersion(),
                dataset.createdAt(), dataset.updatedAt()
        );
    }

    private RagEvaluationDatasetVersionResponse toVersionResponse(RagEvaluationDatasetVersion version) {
        return new RagEvaluationDatasetVersionResponse(
                version.id(), version.datasetId(), version.version(), version.changeNote(),
                version.cases(), version.createdAt()
        );
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
