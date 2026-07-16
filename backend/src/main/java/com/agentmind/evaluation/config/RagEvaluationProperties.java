package com.agentmind.evaluation.config;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 检索增强生成评估配置。
 *
 * <p>价格按每百万令牌配置。评估任务会把运行当时的令牌用量和成本写入快照，
 * 后续修改价格不会反向改变历史结果。</p>
 */
@Component
@ConfigurationProperties(prefix = "agentmind.evaluation")
public class RagEvaluationProperties {

    private BigDecimal inputCostPerMillionTokens = BigDecimal.ZERO;
    private BigDecimal outputCostPerMillionTokens = BigDecimal.ZERO;
    private int maximumCasesPerVersion = 200;
    private String chunkStrategyVersion = "markdown-aware-v1";
    private int executorCorePoolSize = 2;
    private int executorMaxPoolSize = 4;
    private int executorQueueCapacity = 100;
    private int maximumImportBytes = 5 * 1024 * 1024;
    private String instanceId = "";
    private Duration leaseDuration = Duration.ofSeconds(30);
    private Duration heartbeatInterval = Duration.ofSeconds(10);
    private long recoveryFixedDelayMillis = 15_000;
    private long recoveryInitialDelayMillis = 5_000;
    private int recoveryBatchSize = 50;
    private int rrfRankConstant = 60;
    private String judgePromptVersion = "rag-judge-v1";
    private String judgeModelName = "deterministic-local";
    private boolean judgeFailureFallbackEnabled = true;

    public BigDecimal getInputCostPerMillionTokens() {
        return inputCostPerMillionTokens;
    }

    public void setInputCostPerMillionTokens(BigDecimal inputCostPerMillionTokens) {
        this.inputCostPerMillionTokens = inputCostPerMillionTokens;
    }

    public BigDecimal getOutputCostPerMillionTokens() {
        return outputCostPerMillionTokens;
    }

    public void setOutputCostPerMillionTokens(BigDecimal outputCostPerMillionTokens) {
        this.outputCostPerMillionTokens = outputCostPerMillionTokens;
    }

    public int getMaximumCasesPerVersion() {
        return maximumCasesPerVersion;
    }

    public void setMaximumCasesPerVersion(int maximumCasesPerVersion) {
        this.maximumCasesPerVersion = maximumCasesPerVersion;
    }

    public String getChunkStrategyVersion() {
        return chunkStrategyVersion;
    }

    public void setChunkStrategyVersion(String chunkStrategyVersion) {
        this.chunkStrategyVersion = chunkStrategyVersion;
    }

    public int getExecutorCorePoolSize() {
        return executorCorePoolSize;
    }

    public void setExecutorCorePoolSize(int executorCorePoolSize) {
        this.executorCorePoolSize = executorCorePoolSize;
    }

    public int getExecutorMaxPoolSize() {
        return executorMaxPoolSize;
    }

    public void setExecutorMaxPoolSize(int executorMaxPoolSize) {
        this.executorMaxPoolSize = executorMaxPoolSize;
    }

    public int getExecutorQueueCapacity() {
        return executorQueueCapacity;
    }

    public void setExecutorQueueCapacity(int executorQueueCapacity) {
        this.executorQueueCapacity = executorQueueCapacity;
    }

    public int getMaximumImportBytes() {
        return maximumImportBytes;
    }

    public void setMaximumImportBytes(int maximumImportBytes) {
        this.maximumImportBytes = maximumImportBytes;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public long getRecoveryFixedDelayMillis() {
        return recoveryFixedDelayMillis;
    }

    public void setRecoveryFixedDelayMillis(long recoveryFixedDelayMillis) {
        this.recoveryFixedDelayMillis = recoveryFixedDelayMillis;
    }

    public long getRecoveryInitialDelayMillis() {
        return recoveryInitialDelayMillis;
    }

    public void setRecoveryInitialDelayMillis(long recoveryInitialDelayMillis) {
        this.recoveryInitialDelayMillis = recoveryInitialDelayMillis;
    }

    public int getRecoveryBatchSize() {
        return recoveryBatchSize;
    }

    public void setRecoveryBatchSize(int recoveryBatchSize) {
        this.recoveryBatchSize = recoveryBatchSize;
    }

    public int getRrfRankConstant() {
        return rrfRankConstant;
    }

    public void setRrfRankConstant(int rrfRankConstant) {
        this.rrfRankConstant = rrfRankConstant;
    }

    public String getJudgePromptVersion() {
        return judgePromptVersion;
    }

    public void setJudgePromptVersion(String judgePromptVersion) {
        this.judgePromptVersion = judgePromptVersion;
    }

    public String getJudgeModelName() {
        return judgeModelName;
    }

    public void setJudgeModelName(String judgeModelName) {
        this.judgeModelName = judgeModelName;
    }

    public boolean isJudgeFailureFallbackEnabled() {
        return judgeFailureFallbackEnabled;
    }

    public void setJudgeFailureFallbackEnabled(boolean judgeFailureFallbackEnabled) {
        this.judgeFailureFallbackEnabled = judgeFailureFallbackEnabled;
    }
}
