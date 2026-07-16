package com.agentmind.evaluation.service;

import com.agentmind.agent.service.AgentToolExecutionAuthorizer;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.chat.config.RagAnswerGenerationProperties;
import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.common.response.PageResponse;
import com.agentmind.evaluation.config.RagEvaluationProperties;
import com.agentmind.evaluation.model.RagEvaluationDatasetVersion;
import com.agentmind.evaluation.model.RagEvaluationExperimentConfig;
import com.agentmind.evaluation.model.RagEvaluationJob;
import com.agentmind.evaluation.model.RagEvaluationJobStatus;
import com.agentmind.evaluation.model.RagEvaluationMetrics;
import com.agentmind.evaluation.model.RagEvaluationQualityGate;
import com.agentmind.evaluation.model.RagEvaluationRerankStrategy;
import com.agentmind.evaluation.model.RagEvaluationRetrievalStrategy;
import com.agentmind.evaluation.model.dto.RagEvaluationComparisonResponse;
import com.agentmind.evaluation.model.dto.RagEvaluationCaseMetricDeltaResponse;
import com.agentmind.evaluation.model.dto.RagEvaluationDashboardResponse;
import com.agentmind.evaluation.model.dto.RagEvaluationJobResponse;
import com.agentmind.evaluation.model.dto.RagEvaluationMetricDeltaResponse;
import com.agentmind.evaluation.model.dto.RagEvaluationQualityGateRequest;
import com.agentmind.evaluation.model.dto.StartRagEvaluationJobRequest;
import com.agentmind.evaluation.repository.RagEvaluationJobRepository;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 异步固定评估任务应用服务，负责创建、取消、重试、查询与基线对比。 */
@Service
public class RagEvaluationJobService {

    private static final DateTimeFormatter EXPERIMENT_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final RagEvaluationDatasetService datasetService;
    private final RagEvaluationJobRepository repository;
    private final RagEvaluationTaskDispatcher dispatcher;
    private final AgentToolExecutionAuthorizer authorizer;
    private final RagAnswerGenerationProperties ragProperties;
    private final RagEvaluationProperties evaluationProperties;

    public RagEvaluationJobService(
            RagEvaluationDatasetService datasetService,
            RagEvaluationJobRepository repository,
            RagEvaluationTaskDispatcher dispatcher,
            AgentToolExecutionAuthorizer authorizer,
            RagAnswerGenerationProperties ragProperties,
            RagEvaluationProperties evaluationProperties
    ) {
        this.datasetService = datasetService;
        this.repository = repository;
        this.dispatcher = dispatcher;
        this.authorizer = authorizer;
        this.ragProperties = ragProperties;
        this.evaluationProperties = evaluationProperties;
    }

    /** 创建待执行任务后立即返回，实际评估由专用线程池继续处理。 */
    public RagEvaluationJobResponse start(AgentToolExecutionContext context, StartRagEvaluationJobRequest request) {
        authorizer.authorize(context);
        RagEvaluationDatasetVersion version = datasetService.requireVersion(
                context, request.datasetId(), request.datasetVersion()
        );
        RagEvaluationExperimentConfig config = createExperimentConfig(request);
        RagEvaluationJob job = createPendingJob(context, version, config, toQualityGate(request.qualityGate()), null);
        dispatch(job);
        return toResponse(job);
    }

    /** 失败或取消任务使用原始实验快照创建新任务，禁止覆盖原始审计证据。 */
    public RagEvaluationJobResponse retry(AgentToolExecutionContext context, Long jobId) {
        authorizer.authorize(context);
        RagEvaluationJob source = requireJob(context, jobId);
        if (source.status() != RagEvaluationJobStatus.FAILED
                && source.status() != RagEvaluationJobStatus.CANCELED) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "只有失败或已取消任务可以重试");
        }
        RagEvaluationDatasetVersion version = datasetService.requireVersion(
                context, source.datasetId(), source.datasetVersion()
        );
        RagEvaluationJob retry = createPendingJob(
                context, version, source.experimentConfig(), source.qualityGate(), source.id()
        );
        dispatch(retry);
        return toResponse(retry);
    }

    /** 待执行任务直接取消；运行中任务先进入取消请求状态，由执行线程在题目边界安全结束。 */
    public RagEvaluationJobResponse cancel(AgentToolExecutionContext context, Long jobId) {
        authorizer.authorize(context);
        RagEvaluationJob job = requireJob(context, jobId);
        if (job.terminal() || job.status() == RagEvaluationJobStatus.CANCEL_REQUESTED) {
            return toResponse(job);
        }
        RagEvaluationJob next;
        Set<RagEvaluationJobStatus> expected;
        if (job.status() == RagEvaluationJobStatus.PENDING) {
            next = job.withTerminalResult(RagEvaluationJobStatus.CANCELED, null, null,
                    job.caseResults(), "用户取消评估任务");
            expected = Set.of(RagEvaluationJobStatus.PENDING);
        } else {
            next = job.withStatus(RagEvaluationJobStatus.CANCEL_REQUESTED, job.startedAt(), null, "");
            expected = Set.of(RagEvaluationJobStatus.RUNNING);
        }
        return repository.updateIfStatus(next, expected).map(this::toResponse)
                .orElseGet(() -> toResponse(requireJob(context, jobId)));
    }

    public PageResponse<RagEvaluationJobResponse> list(AgentToolExecutionContext context, int page, int pageSize) {
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
            return new RagEvaluationComparisonResponse(
                    current.id(), null, false, "当前任务尚无可用基线", null, List.of()
            );
        }
        RagEvaluationJob baseline = requireJob(context, effectiveBaselineId);
        if (current.status() != RagEvaluationJobStatus.SUCCEEDED
                || baseline.status() != RagEvaluationJobStatus.SUCCEEDED) {
            return new RagEvaluationComparisonResponse(
                    current.id(), baseline.id(), false, "仅成功完成的任务可以进行指标对比", null, List.of()
            );
        }
        if (!current.datasetId().equals(baseline.datasetId())
                || current.datasetVersion() != baseline.datasetVersion()) {
            return new RagEvaluationComparisonResponse(
                    current.id(), baseline.id(), false, "基线必须使用相同的评估集版本", null, List.of()
            );
        }
        return new RagEvaluationComparisonResponse(
                current.id(), baseline.id(), true, "正值表示当前任务指标更高",
                delta(current.metrics(), baseline.metrics()), caseDeltas(current, baseline)
        );
    }

    public RagEvaluationDashboardResponse dashboard(AgentToolExecutionContext context) {
        authorizer.authorize(context);
        List<RagEvaluationJob> recent = repository.findByScope(context.ownerUserId(), context.workspaceId(), 0, 20);
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

    private RagEvaluationJob createPendingJob(
            AgentToolExecutionContext context,
            RagEvaluationDatasetVersion version,
            RagEvaluationExperimentConfig config,
            RagEvaluationQualityGate qualityGate,
            Long retryOfJobId
    ) {
        Long baselineJobId = repository.findLatestSuccessful(
                context.ownerUserId(), context.workspaceId(), version.datasetId(), version.version()
        ).map(RagEvaluationJob::id).orElse(null);
        OffsetDateTime now = OffsetDateTime.now();
        return repository.save(new RagEvaluationJob(
                null, context.ownerUserId(), context.workspaceId(), version.datasetId(), version.version(),
                RagEvaluationJobStatus.PENDING, config.retrievalStrategy().name(), config.topK(),
                config.promptVersion(), config.modelName(), config, baselineJobId, retryOfJobId,
                version.cases().size(), 0, 0, null, qualityGate, null, List.of(), "", now, null, now, null
        ));
    }

    private RagEvaluationExperimentConfig createExperimentConfig(StartRagEvaluationJobRequest request) {
        int topK = request.topK() == null ? 5 : request.topK();
        int candidatePool = request.candidatePoolSize() == null
                ? Math.min(100, Math.max(topK, topK * 4)) : request.candidatePoolSize();
        if (candidatePool < topK) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "候选池大小不能小于TopK");
        }
        String name = request.experimentName() == null || request.experimentName().isBlank()
                ? "实验-" + OffsetDateTime.now().format(EXPERIMENT_TIME) : request.experimentName().trim();
        return new RagEvaluationExperimentConfig(
                name,
                evaluationProperties.getChunkStrategyVersion(),
                request.retrievalStrategy() == null ? RagEvaluationRetrievalStrategy.VECTOR : request.retrievalStrategy(),
                candidatePool,
                request.rerankStrategy() == null ? RagEvaluationRerankStrategy.NONE : request.rerankStrategy(),
                topK,
                ragProperties.getPromptVersion(),
                ragProperties.getModelName()
        );
    }

    private RagEvaluationQualityGate toQualityGate(RagEvaluationQualityGateRequest request) {
        if (request == null) {
            return null;
        }
        return new RagEvaluationQualityGate(
                request.minimumRecallAtK(), request.minimumNdcgAtK(), request.minimumFaithfulness(),
                request.minimumAnswerRelevance(), request.maximumAverageLatencyMillis(),
                request.maximumTotalTokens(), request.maximumEstimatedCostUsd()
        );
    }

    private void dispatch(RagEvaluationJob job) {
        try {
            dispatcher.dispatch(job.ownerUserId(), job.workspaceId(), job.id());
        } catch (RuntimeException exception) {
            String reason = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            repository.updateIfStatus(job.withTerminalResult(
                    RagEvaluationJobStatus.FAILED, null, null, List.of(), limit(reason, 1000)
            ), Set.of(RagEvaluationJobStatus.PENDING));
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "评估任务投递失败");
        }
    }

    private RagEvaluationJob requireJob(AgentToolExecutionContext context, Long jobId) {
        return repository.findByScopeAndId(context.ownerUserId(), context.workspaceId(), jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "评估任务不存在或无权访问"));
    }

    private RagEvaluationMetricDeltaResponse delta(RagEvaluationMetrics current, RagEvaluationMetrics baseline) {
        return new RagEvaluationMetricDeltaResponse(
                round(current.recallAtK() - baseline.recallAtK()),
                round(current.meanReciprocalRank() - baseline.meanReciprocalRank()),
                round(current.ndcgAtK() - baseline.ndcgAtK()),
                round(current.citationCoverage() - baseline.citationCoverage()),
                round(current.refusalAccuracy() - baseline.refusalAccuracy()),
                round(current.answerKeywordCoverage() - baseline.answerKeywordCoverage()),
                round(current.faithfulness() - baseline.faithfulness()),
                round(current.answerRelevance() - baseline.answerRelevance()),
                current.averageRetrievalMillis() - baseline.averageRetrievalMillis(),
                current.averageRerankMillis() - baseline.averageRerankMillis(),
                current.averageGenerationMillis() - baseline.averageGenerationMillis(),
                current.averageLatencyMillis() - baseline.averageLatencyMillis(),
                current.totalTokens() - baseline.totalTokens(),
                current.estimatedCostUsd().subtract(baseline.estimatedCostUsd()).setScale(6, RoundingMode.HALF_UP)
        );
    }

    private List<RagEvaluationCaseMetricDeltaResponse> caseDeltas(
            RagEvaluationJob current,
            RagEvaluationJob baseline
    ) {
        Map<String, com.agentmind.evaluation.model.RagEvaluationCaseResult> baselineCases = baseline.caseResults()
                .stream().collect(Collectors.toMap(
                        com.agentmind.evaluation.model.RagEvaluationCaseResult::caseKey,
                        Function.identity()
                ));
        return current.caseResults().stream().filter(value -> baselineCases.containsKey(value.caseKey()))
                .map(value -> {
                    var old = baselineCases.get(value.caseKey());
                    return new RagEvaluationCaseMetricDeltaResponse(
                            value.caseKey(), round(value.recallAtK() - old.recallAtK()),
                            round(value.reciprocalRank() - old.reciprocalRank()),
                            round(value.ndcgAtK() - old.ndcgAtK()),
                            value.citationCovered() != old.citationCovered(),
                            value.refusalCorrect() != old.refusalCorrect(),
                            round(value.answerKeywordCoverage() - old.answerKeywordCoverage()),
                            round(value.faithfulness() - old.faithfulness()),
                            round(value.answerRelevance() - old.answerRelevance()),
                            value.elapsedMillis() - old.elapsedMillis(),
                            value.promptTokens() + value.completionTokens()
                                    - old.promptTokens() - old.completionTokens()
                    );
                }).toList();
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
                job.promptVersion(), job.modelName(), job.experimentConfig(), job.baselineJobId(), job.retryOfJobId(),
                job.totalCases(), job.completedCases(), job.progress(), job.terminal(), job.metrics(), job.qualityGate(),
                job.qualityGateResult(), job.caseResults(), job.failureReason(), job.createdAt(), job.startedAt(),
                job.updatedAt(), job.completedAt()
        );
    }
}
