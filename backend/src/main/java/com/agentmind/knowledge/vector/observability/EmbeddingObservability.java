package com.agentmind.knowledge.vector.observability;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 向量模型调用的统一观测入口。
 *
 * <p>指标标签只使用模型和结果两个低基数字段。文本、用户、知识空间和文档编号均不进入日志或指标，
 * 避免泄露私人知识内容，也防止监控系统产生高基数时间序列。</p>
 */
@Component
public class EmbeddingObservability {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingObservability.class);
    private final MeterRegistry meterRegistry;

    public EmbeddingObservability(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(EmbeddingCallObservation observation) {
        String model = safe(observation.modelName());
        String outcome = observation.succeeded() ? "success" : "failure";
        meterRegistry.counter("agentmind.embedding.calls", "model", model, "outcome", outcome).increment();
        meterRegistry.counter("agentmind.embedding.inputs", "model", model).increment(observation.inputCount());
        meterRegistry.counter("agentmind.embedding.tokens", "model", model).increment(observation.inputTokens());
        DistributionSummary.builder("agentmind.embedding.cost.usd")
                .tag("model", model)
                .register(meterRegistry)
                .record(observation.estimatedCostUsd().doubleValue());
        DistributionSummary.builder("agentmind.embedding.attempts")
                .tag("model", model)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(observation.attempts());
        Timer.builder("agentmind.embedding.duration")
                .tag("model", model)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(observation.durationMillis(), TimeUnit.MILLISECONDS);

        if (observation.succeeded()) {
            log.info("向量模型调用完成，模型={}，输入数={}，Token={}，预估费用美元={}，耗时毫秒={}，尝试次数={}",
                    observation.modelName(), observation.inputCount(), observation.inputTokens(),
                    observation.estimatedCostUsd(), observation.durationMillis(), observation.attempts());
        } else {
            log.warn("向量模型调用失败，模型={}，输入数={}，耗时毫秒={}，尝试次数={}，失败类型={}",
                    observation.modelName(), observation.inputCount(), observation.durationMillis(),
                    observation.attempts(), observation.failureType());
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value.toLowerCase(Locale.ROOT);
    }
}
