package com.agentmind.evaluation.config;

import java.math.BigDecimal;
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
}
