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
import com.agentmind.evaluation.config.RagEvaluationProperties;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.agentmind.evaluation.observability.RagEvaluationObservability;

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
    private final RagEvaluationProperties properties;
    private final RagEvaluationInstanceIdentity instanceIdentity;
    private final RagEvaluationLeaseHeartbeat leaseHeartbeat;
    private final RagEvaluationObservability observability;

    public RagEvaluationTaskRunner(
            RagEvaluationDatasetService datasetService,
            RagEvaluationJobRepository repository,
            RagEvaluationProbe probe,
            RagEvaluationMetricCalculator metricCalculator,
            RagEvaluationQualityGateEvaluator qualityGateEvaluator,
            RagEvaluationProperties properties,
            RagEvaluationInstanceIdentity instanceIdentity,
            RagEvaluationLeaseHeartbeat leaseHeartbeat,
            RagEvaluationObservability observability
    ) {
        this.datasetService = datasetService;
        this.repository = repository;
        this.probe = probe;
        this.metricCalculator = metricCalculator;
        this.qualityGateEvaluator = qualityGateEvaluator;
        this.properties = properties;
        this.instanceIdentity = instanceIdentity;
        this.leaseHeartbeat = leaseHeartbeat;
        this.observability = observability;
    }

    public void run(Long ownerUserId, Long workspaceId, Long jobId) {
        OffsetDateTime now = OffsetDateTime.now();
        String leaseOwner = instanceIdentity.value();
        RagEvaluationJob running = repository.claim(
                ownerUserId, workspaceId, jobId, leaseOwner, now, now.plus(properties.getLeaseDuration())
        ).orElse(null);
        if (running == null) {
            return;
        }
        observability.recordJobOutcome("started");
        List<RagEvaluationCaseResult> results = new ArrayList<>(running.caseResults());
        try (RagEvaluationLeaseHeartbeat.Handle heartbeat = leaseHeartbeat.start(jobId, leaseOwner)) {
            AgentToolExecutionContext context = new AgentToolExecutionContext(ownerUserId, workspaceId, null);
            RagEvaluationDatasetVersion version = datasetService.requireVersion(
                    context, running.datasetId(), running.datasetVersion()
            );
            for (RagEvaluationCase evaluationCase : version.cases()) {
                if (alreadyCompleted(results, evaluationCase.caseKey())) {
                    continue;
                }
                if (!heartbeat.owned()) {
                    return;
                }
                RagEvaluationJob latest = find(ownerUserId, workspaceId, jobId);
                if (latest.status() == RagEvaluationJobStatus.CANCEL_REQUESTED) {
                    cancel(latest, results, leaseOwner);
                    return;
                }
                if (latest.status() != RagEvaluationJobStatus.RUNNING
                        || !leaseOwner.equals(latest.leaseOwner())) {
                    return;
                }
                RagEvaluationCaseResult result = metricCalculator.calculateCase(
                        evaluationCase,
                        probe.execute(ownerUserId, workspaceId, evaluationCase.question(), latest.experimentConfig())
                );
                results.add(result);
                if (repository.updateIfStatusAndLeaseOwner(
                        latest.withProgress(results.size(), results), RUNNING, leaseOwner, OffsetDateTime.now()
                ).isEmpty()) {
                    finishCancelIfRequested(ownerUserId, workspaceId, jobId, results, leaseOwner);
                    return;
                }
            }
            RagEvaluationJob latest = find(ownerUserId, workspaceId, jobId);
            if (latest.status() == RagEvaluationJobStatus.CANCEL_REQUESTED) {
                cancel(latest, results, leaseOwner);
                return;
            }
            RagEvaluationMetrics metrics = metricCalculator.aggregate(results);
            boolean succeeded = repository.updateIfStatusAndLeaseOwner(latest.withTerminalResult(
                    RagEvaluationJobStatus.SUCCEEDED,
                    metrics,
                    qualityGateEvaluator.evaluate(latest.qualityGate(), metrics),
                    results,
                    ""
            ), RUNNING, leaseOwner, OffsetDateTime.now()).orElseGet(() -> {
                finishCancelIfRequested(ownerUserId, workspaceId, jobId, results, leaseOwner);
                return null;
            }) != null;
            if (succeeded) {
                observability.recordJobOutcome("succeeded");
            }
        } catch (RuntimeException exception) {
            fail(ownerUserId, workspaceId, jobId, results, leaseOwner, exception);
        }
    }

    private void finishCancelIfRequested(
            Long ownerUserId,
            Long workspaceId,
            Long jobId,
            List<RagEvaluationCaseResult> results,
            String leaseOwner
    ) {
        RagEvaluationJob latest = find(ownerUserId, workspaceId, jobId);
        if (latest.status() == RagEvaluationJobStatus.CANCEL_REQUESTED) {
            cancel(latest, results, leaseOwner);
        }
    }

    private void cancel(RagEvaluationJob job, List<RagEvaluationCaseResult> results, String leaseOwner) {
        repository.updateIfStatusAndLeaseOwner(job.withTerminalResult(
                RagEvaluationJobStatus.CANCELED, null, null, results, "用户取消评估任务"
        ), Set.of(RagEvaluationJobStatus.CANCEL_REQUESTED), leaseOwner, OffsetDateTime.now())
                .ifPresent(ignored -> observability.recordJobOutcome("canceled"));
    }

    private void fail(
            Long ownerUserId,
            Long workspaceId,
            Long jobId,
            List<RagEvaluationCaseResult> results,
            String leaseOwner,
            RuntimeException exception
    ) {
        RagEvaluationJob latest = find(ownerUserId, workspaceId, jobId);
        if (latest.status() == RagEvaluationJobStatus.CANCEL_REQUESTED) {
            cancel(latest, results, leaseOwner);
            return;
        }
        String reason = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        repository.updateIfStatusAndLeaseOwner(latest.withTerminalResult(
                RagEvaluationJobStatus.FAILED, null, null, results, limit(reason, 1000)
        ), RUNNING, leaseOwner, OffsetDateTime.now())
                .ifPresent(ignored -> observability.recordJobOutcome("failed"));
        LOGGER.warn("评估任务执行失败，任务编号={}，原因={}", jobId, reason, exception);
    }

    private boolean alreadyCompleted(List<RagEvaluationCaseResult> results, String caseKey) {
        return results.stream().anyMatch(result -> result.caseKey().equals(caseKey));
    }

    private RagEvaluationJob find(Long ownerUserId, Long workspaceId, Long jobId) {
        return repository.findByScopeAndId(ownerUserId, workspaceId, jobId)
                .orElseThrow(() -> new IllegalStateException("评估任务不存在：" + jobId));
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
