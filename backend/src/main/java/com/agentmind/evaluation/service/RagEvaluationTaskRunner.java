package com.agentmind.evaluation.service;

import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.evaluation.metric.RagEvaluationMetricCalculator;
import com.agentmind.evaluation.metric.RagEvaluationQualityGateEvaluator;
import com.agentmind.evaluation.model.RagEvaluationCase;
import com.agentmind.evaluation.model.RagEvaluationCaseResult;
import com.agentmind.evaluation.model.RagEvaluationDatasetVersion;
import com.agentmind.evaluation.model.RagEvaluationJob;
import com.agentmind.evaluation.model.RagEvaluationJobStatus;
import com.agentmind.evaluation.model.RagEvaluationMetrics;
import com.agentmind.evaluation.repository.RagEvaluationJobRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 异步评估任务执行器。
 *
 * <p>每完成一道题就以条件更新方式保存进度和逐题证据。取消请求、执行完成与失败更新都通过
 * 仓储的状态比较更新竞争，因此即使部署多个实例，也不会把已取消任务覆盖成成功。</p>
 */
@Component
public class RagEvaluationTaskRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(RagEvaluationTaskRunner.class);
    private static final Set<RagEvaluationJobStatus> RUNNING = Set.of(RagEvaluationJobStatus.RUNNING);

    private final RagEvaluationDatasetService datasetService;
    private final RagEvaluationJobRepository repository;
    private final RagEvaluationProbe probe;
    private final RagEvaluationMetricCalculator metricCalculator;
    private final RagEvaluationQualityGateEvaluator qualityGateEvaluator;

    public RagEvaluationTaskRunner(
            RagEvaluationDatasetService datasetService,
            RagEvaluationJobRepository repository,
            RagEvaluationProbe probe,
            RagEvaluationMetricCalculator metricCalculator,
            RagEvaluationQualityGateEvaluator qualityGateEvaluator
    ) {
        this.datasetService = datasetService;
        this.repository = repository;
        this.probe = probe;
        this.metricCalculator = metricCalculator;
        this.qualityGateEvaluator = qualityGateEvaluator;
    }

    public void run(Long ownerUserId, Long workspaceId, Long jobId) {
        RagEvaluationJob pending = find(ownerUserId, workspaceId, jobId);
        RagEvaluationJob running = repository.updateIfStatus(
                pending.withStatus(RagEvaluationJobStatus.RUNNING, OffsetDateTime.now(), null, ""),
                Set.of(RagEvaluationJobStatus.PENDING)
        ).orElse(null);
        if (running == null) {
            return;
        }
        List<RagEvaluationCaseResult> results = new ArrayList<>(running.caseResults());
        try {
            AgentToolExecutionContext context = new AgentToolExecutionContext(ownerUserId, workspaceId, null);
            RagEvaluationDatasetVersion version = datasetService.requireVersion(
                    context, running.datasetId(), running.datasetVersion()
            );
            for (RagEvaluationCase evaluationCase : version.cases()) {
                RagEvaluationJob latest = find(ownerUserId, workspaceId, jobId);
                if (latest.status() == RagEvaluationJobStatus.CANCEL_REQUESTED) {
                    cancel(latest, results);
                    return;
                }
                if (latest.status() != RagEvaluationJobStatus.RUNNING) {
                    return;
                }
                RagEvaluationCaseResult result = metricCalculator.calculateCase(
                        evaluationCase,
                        probe.execute(ownerUserId, workspaceId, evaluationCase.question(), latest.experimentConfig())
                );
                results.add(result);
                if (repository.updateIfStatus(latest.withProgress(results.size(), results), RUNNING).isEmpty()) {
                    finishCancelIfRequested(ownerUserId, workspaceId, jobId, results);
                    return;
                }
            }
            RagEvaluationJob latest = find(ownerUserId, workspaceId, jobId);
            if (latest.status() == RagEvaluationJobStatus.CANCEL_REQUESTED) {
                cancel(latest, results);
                return;
            }
            RagEvaluationMetrics metrics = metricCalculator.aggregate(results);
            repository.updateIfStatus(latest.withTerminalResult(
                    RagEvaluationJobStatus.SUCCEEDED,
                    metrics,
                    qualityGateEvaluator.evaluate(latest.qualityGate(), metrics),
                    results,
                    ""
            ), RUNNING).orElseGet(() -> {
                finishCancelIfRequested(ownerUserId, workspaceId, jobId, results);
                return null;
            });
        } catch (RuntimeException exception) {
            fail(ownerUserId, workspaceId, jobId, results, exception);
        }
    }

    private void finishCancelIfRequested(
            Long ownerUserId,
            Long workspaceId,
            Long jobId,
            List<RagEvaluationCaseResult> results
    ) {
        RagEvaluationJob latest = find(ownerUserId, workspaceId, jobId);
        if (latest.status() == RagEvaluationJobStatus.CANCEL_REQUESTED) {
            cancel(latest, results);
        }
    }

    private void cancel(RagEvaluationJob job, List<RagEvaluationCaseResult> results) {
        repository.updateIfStatus(job.withTerminalResult(
                RagEvaluationJobStatus.CANCELED, null, null, results, "用户取消评估任务"
        ), Set.of(RagEvaluationJobStatus.CANCEL_REQUESTED));
    }

    private void fail(
            Long ownerUserId,
            Long workspaceId,
            Long jobId,
            List<RagEvaluationCaseResult> results,
            RuntimeException exception
    ) {
        RagEvaluationJob latest = find(ownerUserId, workspaceId, jobId);
        if (latest.status() == RagEvaluationJobStatus.CANCEL_REQUESTED) {
            cancel(latest, results);
            return;
        }
        String reason = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        repository.updateIfStatus(latest.withTerminalResult(
                RagEvaluationJobStatus.FAILED, null, null, results, limit(reason, 1000)
        ), RUNNING);
        LOGGER.warn("评估任务执行失败，任务编号={}，原因={}", jobId, reason, exception);
    }

    private RagEvaluationJob find(Long ownerUserId, Long workspaceId, Long jobId) {
        return repository.findByScopeAndId(ownerUserId, workspaceId, jobId)
                .orElseThrow(() -> new IllegalStateException("评估任务不存在：" + jobId));
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
