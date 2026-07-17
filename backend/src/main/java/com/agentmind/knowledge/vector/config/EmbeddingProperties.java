package com.agentmind.knowledge.vector.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 项目级向量模型配置。
 *
 * <p>模型价格不在代码中写死，维护者需要根据实际供应商账单设置每百万输入 Token 的价格。
 * 向量维度必须与数据库向量列保持一致，适配器会在写库前校验每个模型响应。</p>
 */
@Validated
@Component
@ConfigurationProperties(prefix = "agentmind.embedding")
public class EmbeddingProperties {

    @NotBlank
    private String provider = "deterministic";
    @NotBlank
    private String modelName = "deterministic-local";
    @Min(1)
    private int dimensions = 128;
    @Min(1)
    private int batchSize = 32;
    @Min(1)
    private int maximumAttempts = 3;
    @NotNull
    private Duration retryInitialBackoff = Duration.ofMillis(200);
    @NotNull
    @DecimalMin("0")
    private BigDecimal inputCostPerMillionTokens = BigDecimal.ZERO;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public int getDimensions() {
        return dimensions;
    }

    public void setDimensions(int dimensions) {
        this.dimensions = dimensions;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaximumAttempts() {
        return maximumAttempts;
    }

    public void setMaximumAttempts(int maximumAttempts) {
        this.maximumAttempts = maximumAttempts;
    }

    public Duration getRetryInitialBackoff() {
        return retryInitialBackoff;
    }

    public void setRetryInitialBackoff(Duration retryInitialBackoff) {
        this.retryInitialBackoff = retryInitialBackoff;
    }

    public BigDecimal getInputCostPerMillionTokens() {
        return inputCostPerMillionTokens;
    }

    public void setInputCostPerMillionTokens(BigDecimal inputCostPerMillionTokens) {
        this.inputCostPerMillionTokens = inputCostPerMillionTokens;
    }
}
