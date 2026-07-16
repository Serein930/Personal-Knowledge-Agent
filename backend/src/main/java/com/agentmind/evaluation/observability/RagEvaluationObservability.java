package com.agentmind.evaluation.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 评估链路的统一观测入口。
 *
 * <p>这里只允许阶段、策略和结果等低基数标签。任务编号、用户编号与知识空间编号仅进入业务日志，
 * 不能进入指标标签，否则会在长期运行后制造不可控的时间序列数量。</p>
 */
@Component
public class RagEvaluationObservability {

    private final ObservationRegistry observationRegistry;
    private final MeterRegistry meterRegistry;

    public RagEvaluationObservability(
            ObservationRegistry observationRegistry,
            MeterRegistry meterRegistry
    ) {
        this.observationRegistry = observationRegistry;
        this.meterRegistry = meterRegistry;
    }

    public <T> T observePhase(String phase, String strategy, Supplier<T> action) {
        return Observation.createNotStarted("agentmind.rag.evaluation.phase", observationRegistry)
                .lowCardinalityKeyValue("phase", phase)
                .lowCardinalityKeyValue("strategy", safe(strategy))
                .observe(action);
    }

    public void recordJobOutcome(String outcome) {
        counter("agentmind.rag.evaluation.jobs", "outcome", safe(outcome)).increment();
    }

    public void recordLeaseRenewalFailure() {
        counter("agentmind.rag.evaluation.lease.renewal.failures", "reason", "ownership_lost").increment();
    }

    public void recordRecovery(String outcome, long count) {
        if (count > 0) {
            counter("agentmind.rag.evaluation.lease.recoveries", "outcome", safe(outcome)).increment(count);
        }
    }

    private Counter counter(String name, String tagName, String tagValue) {
        return Counter.builder(name).tag(tagName, tagValue).register(meterRegistry);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value.toLowerCase(java.util.Locale.ROOT);
    }
}
