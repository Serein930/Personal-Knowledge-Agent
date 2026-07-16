package com.agentmind.evaluation.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * 异步评估任务及其可复现实验快照。
 *
 * <p>任务从待执行进入运行中，可被请求取消，最终进入成功、失败或已取消状态。每次更新都保留
 * 数据集版本、实验配置、质量门禁、进度和逐题证据，供进度接口、失败重试和基线比较复用。</p>
 */
public record RagEvaluationJob(
        Long id,
        Long ownerUserId,
        Long workspaceId,
        Long datasetId,
        int datasetVersion,
        RagEvaluationJobStatus status,
        String retrievalStrategy,
        int topK,
        String promptVersion,
        String modelName,
        RagEvaluationExperimentConfig experimentConfig,
        Long baselineJobId,
        Long retryOfJobId,
        int totalCases,
        int completedCases,
        int progress,
        RagEvaluationMetrics metrics,
        RagEvaluationQualityGate qualityGate,
        RagEvaluationQualityGateResult qualityGateResult,
        List<RagEvaluationCaseResult> caseResults,
        String failureReason,
        int attemptCount,
        int recoveryCount,
        String leaseOwner,
        OffsetDateTime leaseExpiresAt,
        OffsetDateTime heartbeatAt,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt
) {

    private static final Set<RagEvaluationJobStatus> TERMINAL_STATUSES = Set.of(
            RagEvaluationJobStatus.SUCCEEDED,
            RagEvaluationJobStatus.FAILED,
            RagEvaluationJobStatus.CANCELED
    );

    public boolean terminal() {
        return TERMINAL_STATUSES.contains(status);
    }

    public RagEvaluationJob withStatus(
            RagEvaluationJobStatus nextStatus,
            OffsetDateTime nextStartedAt,
            OffsetDateTime nextCompletedAt,
            String nextFailureReason
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        return copy(nextStatus, completedCases, progress, metrics, qualityGateResult, caseResults,
                nextFailureReason, attemptCount, recoveryCount, leaseOwner, leaseExpiresAt, heartbeatAt,
                nextStartedAt, now, nextCompletedAt);
    }

    public RagEvaluationJob withProgress(int nextCompletedCases, List<RagEvaluationCaseResult> nextResults) {
        int nextProgress = totalCases == 0 ? 100 : Math.min(100, nextCompletedCases * 100 / totalCases);
        return copy(status, nextCompletedCases, nextProgress, metrics, qualityGateResult, nextResults,
                failureReason, attemptCount, recoveryCount, leaseOwner, leaseExpiresAt, heartbeatAt,
                startedAt, OffsetDateTime.now(), completedAt);
    }

    public RagEvaluationJob withTerminalResult(
            RagEvaluationJobStatus terminalStatus,
            RagEvaluationMetrics nextMetrics,
            RagEvaluationQualityGateResult nextQualityGateResult,
            List<RagEvaluationCaseResult> nextResults,
            String nextFailureReason
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        int finalProgress = terminalStatus == RagEvaluationJobStatus.SUCCEEDED ? 100 : progress;
        return copy(terminalStatus, nextResults.size(), finalProgress, nextMetrics, nextQualityGateResult,
                nextResults, nextFailureReason, attemptCount, recoveryCount, "", null, heartbeatAt,
                startedAt, now, now);
    }

    /** 创建当前实例成功取得租约后的任务快照。 */
    public RagEvaluationJob withLease(String nextLeaseOwner, OffsetDateTime now, OffsetDateTime nextLeaseExpiresAt) {
        return copy(RagEvaluationJobStatus.RUNNING, completedCases, progress, metrics, qualityGateResult, caseResults,
                failureReason, attemptCount + 1, recoveryCount, nextLeaseOwner, nextLeaseExpiresAt, now,
                startedAt == null ? now : startedAt, now, completedAt);
    }

    /** 心跳只延长租约，不改变业务进度。 */
    public RagEvaluationJob withRenewedLease(OffsetDateTime now, OffsetDateTime nextLeaseExpiresAt) {
        return copy(status, completedCases, progress, metrics, qualityGateResult, caseResults,
                failureReason, attemptCount, recoveryCount, leaseOwner, nextLeaseExpiresAt, now,
                startedAt, now, completedAt);
    }

    /** 失联任务恢复时保留已完成题目，使新实例可以断点续跑。 */
    public RagEvaluationJob withRecoveredStatus(RagEvaluationJobStatus nextStatus, OffsetDateTime now) {
        OffsetDateTime terminalAt = nextStatus == RagEvaluationJobStatus.CANCELED ? now : completedAt;
        String reason = nextStatus == RagEvaluationJobStatus.CANCELED ? "取消请求期间执行实例失联，任务已安全取消" : failureReason;
        return copy(nextStatus, completedCases, progress, metrics, qualityGateResult, caseResults,
                reason, attemptCount, recoveryCount + 1, "", null, heartbeatAt,
                startedAt, now, terminalAt);
    }

    private RagEvaluationJob copy(
            RagEvaluationJobStatus nextStatus,
            int nextCompletedCases,
            int nextProgress,
            RagEvaluationMetrics nextMetrics,
            RagEvaluationQualityGateResult nextQualityGateResult,
            List<RagEvaluationCaseResult> nextResults,
            String nextFailureReason,
            int nextAttemptCount,
            int nextRecoveryCount,
            String nextLeaseOwner,
            OffsetDateTime nextLeaseExpiresAt,
            OffsetDateTime nextHeartbeatAt,
            OffsetDateTime nextStartedAt,
            OffsetDateTime nextUpdatedAt,
            OffsetDateTime nextCompletedAt
    ) {
        return new RagEvaluationJob(
                id, ownerUserId, workspaceId, datasetId, datasetVersion, nextStatus,
                retrievalStrategy, topK, promptVersion, modelName, experimentConfig,
                baselineJobId, retryOfJobId, totalCases, nextCompletedCases, nextProgress,
                nextMetrics, qualityGate, nextQualityGateResult, List.copyOf(nextResults),
                nextFailureReason, nextAttemptCount, nextRecoveryCount, nextLeaseOwner,
                nextLeaseExpiresAt, nextHeartbeatAt, createdAt, nextStartedAt, nextUpdatedAt, nextCompletedAt
        );
    }
}
