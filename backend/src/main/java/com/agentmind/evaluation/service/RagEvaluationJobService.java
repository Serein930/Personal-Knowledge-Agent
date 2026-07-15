package com.agentmind.evaluation.service;

import com.agentmind.agent.service.AgentToolExecutionAuthorizer;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.chat.config.RagAnswerGenerationProperties;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.common.response.PageResponse;
import com.agentmind.evaluation.metric.RagEvaluationMetricCalculator;
import com.agentmind.evaluation.model.RagEvaluationCaseResult;
import com.agentmind.evaluation.model.RagEvaluationDatasetVersion;
import com.agentmind.evaluation.model.RagEvaluationJob;
import com.agentmind.evaluation.model.RagEvaluationJobStatus;
import com.agentmind.evaluation.model.RagEvaluationMetrics;
import com.agentmind.evaluation.model.dto.RagEvaluationComparisonResponse;
import com.agentmind.evaluation.model.dto.RagEvaluationDashboardResponse;
import com.agentmind.evaluation.model.dto.RagEvaluationJobResponse;
import com.agentmind.evaluation.model.dto.RagEvaluationMetricDeltaResponse;
import com.agentmind.evaluation.model.dto.StartRagEvaluationJobRequest;
import com.agentmind.evaluation.repository.RagEvaluationJobRepository;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 固定评估任务应用服务。
 *
 * <p>当前阶段采用同步执行，适合不超过两百题的本地评估集。任务会先持久化运行中快照，
 * 无论成功或失败都保存最终状态；下一阶段可以在不改变接口契约的情况下替换为异步任务队列。</p>
 */
@Service
public class RagEvaluationJobService {

    private static final String RETRIEVAL_STRATEGY = "向量余弦检索";

    private final RagEvaluationDatasetService datasetService;
    private final RagEvaluationJobRepository repository;
    private final RagEvaluationProbe probe;
    private final RagEvaluationMetricCalculator metricCalculator;
    private final AgentToolExecutionAuthorizer authorizer;
    private final RagAnswerGenerationProperties ragProperties;

    public RagEvaluationJobService(
            RagEvaluationDatasetService datasetService,
            RagEvaluationJobRepository repository,
            RagEvaluationProbe probe,
            RagEvaluationMetricCalculator metricCalculator,
            AgentToolExecutionAuthorizer authorizer,
            RagAnswerGenerationProperties ragProperties
    ) {
        this.datasetService = datasetService;
        this.repository = repository;
        this.probe = probe;
        this.metricCalculator = metricCalculator;
        this.authorizer = authorizer;
        this.ragProperties = ragProperties;
    }

    public RagEvaluationJobResponse start(
            AgentToolExecutionContext context,
            StartRagEvaluationJobRequest request
    ) {
        authorizer.authorize(context);
        RagEvaluationDatasetVersion version = datasetService.requireVersion(
                context, request.datasetId(), request.datasetVersion()
        );
        int topK = request.topK() == null ? 5 : request.topK();
        Long baselineJobId = repository.findLatestSuccessful(
                context.ownerUserId(), context.workspaceId(), version.datasetId(), version.version()
        ).map(RagEvaluationJob::id).orElse(null);
        OffsetDateTime now = OffsetDateTime.now();
        RagEvaluationJob running = repository.save(new RagEvaluationJob(
                null, context.ownerUserId(), context.workspaceId(), version.datasetId(), version.version(),
                RagEvaluationJobStatus.RUNNING, RETRIEVAL_STRATEGY, topK,
                ragProperties.getPromptVersion(), ragProperties.getModelName(), baselineJobId,
                null, List.of(), "", now, now, null
        ));
        try {
            List<RagEvaluationCaseResult> caseResults = version.cases().stream()
                    .map(evaluationCase -> metricCalculator.calculateCase(
                            evaluationCase,
                            probe.execute(context.ownerUserId(), context.workspaceId(), evaluationCase.question(), topK)
                    )).toList();
            RagEvaluationMetrics metrics = metricCalculator.aggregate(caseResults);
            return toResponse(repository.save(finish(
                    running, RagEvaluationJobStatus.SUCCEEDED, metrics, caseResults, ""
            )));
        } catch (RuntimeException exception) {
            String reason = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            return toResponse(repository.save(finish(
                    running, RagEvaluationJobStatus.FAILED, null, List.of(), limit(reason, 1000)
            )));
        }
    }

    public PageResponse<RagEvaluationJobResponse> list(
            AgentToolExecutionContext context,
            int page,
            int pageSize
    ) {
        authorizer.authorize(context);
        List<RagEvaluationJobResponse> records = repository.findByScope(
                context.ownerUserId(), context.workspaceId(), (page - 1) * pageSize, pageSize
        ).stream().map(this::toResponse).toList();
        return new PageResponse<>(records, page, pageSize,
                repository.countByScope(context.ownerUserId(), context.workspaceId(), null));
    }

    public RagEvaluationJobResponse get(AgentToolExecutionContext context, Long jobId) {
        authorizer.authorize(context);
        return toResponse(requireJob(context, jobId));
    }

    public RagEvaluationComparisonResponse compare(
            AgentToolExecutionContext context,
            Long currentJobId,
            Long baselineJobId
    ) {
        authorizer.authorize(context);
        RagEvaluationJob current = requireJob(context, currentJobId);
        Long effectiveBaselineId = baselineJobId == null ? current.baselineJobId() : baselineJobId;
        if (effectiveBaselineId == null) {
            return new RagEvaluationComparisonResponse(current.id(), null, false, "当前任务尚无可用基线", null);
        }
        RagEvaluationJob baseline = requireJob(context, effectiveBaselineId);
        if (current.status() != RagEvaluationJobStatus.SUCCEEDED
                || baseline.status() != RagEvaluationJobStatus.SUCCEEDED) {
            return new RagEvaluationComparisonResponse(
                    current.id(), baseline.id(), false, "仅成功完成的任务可以进行指标对比", null
            );
        }
        if (!current.datasetId().equals(baseline.datasetId())
                || current.datasetVersion() != baseline.datasetVersion()) {
            return new RagEvaluationComparisonResponse(
                    current.id(), baseline.id(), false, "基线必须使用相同的评估集版本", null
            );
        }
        return new RagEvaluationComparisonResponse(
                current.id(), baseline.id(), true, "正值表示当前任务指标更高", delta(current.metrics(), baseline.metrics())
        );
    }

    public RagEvaluationDashboardResponse dashboard(AgentToolExecutionContext context) {
        authorizer.authorize(context);
        List<RagEvaluationJob> recent = repository.findByScope(
                context.ownerUserId(), context.workspaceId(), 0, 20
        );
        // 仪表盘的最近列表只截取二十条；成功任务可能更早，因此必须通过仓储单独查询，不能从列表猜测。
        RagEvaluationJob latest = repository.findLatestSuccessfulByScope(
                context.ownerUserId(), context.workspaceId()
        ).orElse(null);
        RagEvaluationComparisonResponse comparison = latest == null
                ? null : compare(context, latest.id(), latest.baselineJobId());
        return new RagEvaluationDashboardResponse(
                datasetService.list(context, 1, 1).total(),
                repository.countByScope(context.ownerUserId(), context.workspaceId(), null),
                repository.countByScope(context.ownerUserId(), context.workspaceId(), RagEvaluationJobStatus.SUCCEEDED),
                latest == null ? null : toResponse(latest), comparison,
                recent.stream().map(this::toResponse).toList()
        );
    }

    private RagEvaluationJob finish(
            RagEvaluationJob running,
            RagEvaluationJobStatus status,
            RagEvaluationMetrics metrics,
            List<RagEvaluationCaseResult> caseResults,
            String failureReason
    ) {
        return new RagEvaluationJob(
                running.id(), running.ownerUserId(), running.workspaceId(), running.datasetId(),
                running.datasetVersion(), status, running.retrievalStrategy(), running.topK(),
                running.promptVersion(), running.modelName(), running.baselineJobId(), metrics, caseResults,
                failureReason, running.createdAt(), running.startedAt(), OffsetDateTime.now()
        );
    }

    private RagEvaluationJob requireJob(AgentToolExecutionContext context, Long jobId) {
        return repository.findByScopeAndId(context.ownerUserId(), context.workspaceId(), jobId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "评估任务不存在或无权访问"
                ));
    }

    private RagEvaluationMetricDeltaResponse delta(RagEvaluationMetrics current, RagEvaluationMetrics baseline) {
        return new RagEvaluationMetricDeltaResponse(
                round(current.recallAtK() - baseline.recallAtK()),
                round(current.meanReciprocalRank() - baseline.meanReciprocalRank()),
                round(current.citationCoverage() - baseline.citationCoverage()),
                round(current.refusalAccuracy() - baseline.refusalAccuracy()),
                round(current.answerKeywordCoverage() - baseline.answerKeywordCoverage()),
                current.averageLatencyMillis() - baseline.averageLatencyMillis(),
                current.totalTokens() - baseline.totalTokens(),
                current.estimatedCostUsd().subtract(baseline.estimatedCostUsd()).setScale(6, RoundingMode.HALF_UP)
        );
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private RagEvaluationJobResponse toResponse(RagEvaluationJob job) {
        return new RagEvaluationJobResponse(
                job.id(), job.datasetId(), job.datasetVersion(), job.status(), job.retrievalStrategy(), job.topK(),
                job.promptVersion(), job.modelName(), job.baselineJobId(), job.metrics(), job.caseResults(),
                job.failureReason(), job.createdAt(), job.startedAt(), job.completedAt()
        );
    }
}
