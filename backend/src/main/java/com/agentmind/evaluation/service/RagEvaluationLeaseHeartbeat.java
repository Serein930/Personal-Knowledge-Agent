package com.agentmind.evaluation.service;

import com.agentmind.evaluation.config.RagEvaluationProperties;
import com.agentmind.evaluation.repository.RagEvaluationJobRepository;
import java.time.OffsetDateTime;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import com.agentmind.evaluation.observability.RagEvaluationObservability;

/** 在任务执行期间独立续租，并向执行线程暴露租约是否仍然有效。 */
@Component
public class RagEvaluationLeaseHeartbeat {

    private static final Logger LOGGER = LoggerFactory.getLogger(RagEvaluationLeaseHeartbeat.class);

    private final RagEvaluationJobRepository repository;
    private final RagEvaluationProperties properties;
    private final TaskScheduler scheduler;
    private final RagEvaluationObservability observability;

    public RagEvaluationLeaseHeartbeat(
            RagEvaluationJobRepository repository,
            RagEvaluationProperties properties,
            @Qualifier("ragEvaluationHeartbeatScheduler") TaskScheduler scheduler,
            RagEvaluationObservability observability
    ) {
        this.repository = repository;
        this.properties = properties;
        this.scheduler = scheduler;
        this.observability = observability;
    }

    public Handle start(Long jobId, String leaseOwner) {
        AtomicBoolean owned = new AtomicBoolean(true);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            OffsetDateTime now = OffsetDateTime.now();
            try {
                boolean renewed = repository.renewLease(
                        jobId, leaseOwner, now, now.plus(properties.getLeaseDuration())
                );
                if (!renewed) {
                    owned.set(false);
                    observability.recordLeaseRenewalFailure();
                    LOGGER.warn("评估任务租约续期失败，停止当前实例写入：任务编号={}，实例={}", jobId, leaseOwner);
                }
            } catch (RuntimeException exception) {
                // 数据库瞬时故障不立即宣告丢失租约；最终由租约截止时间和条件更新共同裁决所有权。
                LOGGER.warn("评估任务心跳暂时写入失败：任务编号={}，实例={}", jobId, leaseOwner, exception);
            }
        }, properties.getHeartbeatInterval());
        return new Handle(owned, future);
    }

    public static final class Handle implements AutoCloseable {

        private final AtomicBoolean owned;
        private final ScheduledFuture<?> future;

        private Handle(AtomicBoolean owned, ScheduledFuture<?> future) {
            this.owned = owned;
            this.future = future;
        }

        public boolean owned() {
            return owned.get();
        }

        @Override
        public void close() {
            future.cancel(false);
        }
    }
}
