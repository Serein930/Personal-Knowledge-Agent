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
}
