package com.agentmind.evaluation.service;

import com.agentmind.evaluation.config.RagEvaluationProperties;
import com.agentmind.evaluation.model.RagEvaluationJob;
import com.agentmind.evaluation.model.RagEvaluationJobStatus;
import com.agentmind.evaluation.repository.RagEvaluationJobRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.agentmind.evaluation.observability.RagEvaluationObservability;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 恢复因进程退出、容器重启或网络隔离而失去心跳的评估任务。
 *
 * <p>仓储通过数据库行锁和跳过已锁定记录保证多个实例可以同时扫描。恢复为待执行后仍需再次原子领取，
 * 因此重复投递不会导致重复执行。</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "agentmind.evaluation",
        name = "recovery-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RagEvaluationLeaseRecoveryScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RagEvaluationLeaseRecoveryScheduler.class);

    private final RagEvaluationJobRepository repository;
    private final RagEvaluationTaskDispatcher dispatcher;
    private final RagEvaluationProperties properties;
    private final RagEvaluationObservability observability;
    private final Set<Long> dispatchedJobIds = ConcurrentHashMap.newKeySet();

    public RagEvaluationLeaseRecoveryScheduler(
            RagEvaluationJobRepository repository,
            RagEvaluationTaskDispatcher dispatcher,
            RagEvaluationProperties properties,
            RagEvaluationObservability observability
    ) {
        this.repository = repository;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.observability = observability;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterStartup() {
        recover();
    }

    @Scheduled(
            fixedDelayString = "${agentmind.evaluation.recovery-fixed-delay-millis:15000}",
            initialDelayString = "${agentmind.evaluation.recovery-initial-delay-millis:5000}"
    )
    public void recover() {
        List<RagEvaluationJob> pendingBeforeRecovery = repository.findPendingJobs(properties.getRecoveryBatchSize());
        List<RagEvaluationJob> recovered = repository.recoverExpiredLeases(
                OffsetDateTime.now(), properties.getRecoveryBatchSize()
        );
        // 失联恢复后的同一任务允许重新投递；普通待执行任务每个实例只入队一次，避免周期扫描灌满线程池队列。
        recovered.forEach(job -> dispatchedJobIds.remove(job.id()));
        java.util.stream.Stream.concat(
                        pendingBeforeRecovery.stream(),
                        recovered.stream().filter(job -> job.status() == RagEvaluationJobStatus.PENDING)
                )
                .distinct()
                .filter(job -> job.status() == RagEvaluationJobStatus.PENDING)
                .forEach(this::dispatchSafely);
        if (!recovered.isEmpty()) {
            long redispatched = recovered.stream().filter(job -> job.status() == RagEvaluationJobStatus.PENDING).count();
            observability.recordRecovery("redispatched", redispatched);
            observability.recordRecovery("canceled", recovered.size() - redispatched);
            LOGGER.info("完成评估任务失联恢复：总数={}，重新投递={}，安全取消={}",
                    recovered.size(), redispatched, recovered.size() - redispatched);
        }
    }

    private void dispatchSafely(RagEvaluationJob job) {
        if (!dispatchedJobIds.add(job.id())) {
            return;
        }
        try {
            dispatcher.dispatch(job.ownerUserId(), job.workspaceId(), job.id());
        } catch (RuntimeException exception) {
            dispatchedJobIds.remove(job.id());
            // 保持 PENDING 状态，下一轮维护会再次投递；不能因线程池瞬时拥塞永久丢失任务。
            LOGGER.warn("评估待执行任务恢复投递失败，将在下一轮重试：任务编号={}", job.id(), exception);
        }
    }
}
