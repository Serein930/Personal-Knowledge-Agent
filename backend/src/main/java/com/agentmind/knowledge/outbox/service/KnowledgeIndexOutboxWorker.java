package com.agentmind.knowledge.outbox.service;

import com.agentmind.knowledge.keyword.KeywordIndex;
import com.agentmind.knowledge.keyword.KeywordIndexDocument;
import com.agentmind.knowledge.outbox.config.KnowledgeIndexOutboxProperties;
import com.agentmind.knowledge.outbox.model.KnowledgeIndexOutboxEvent;
import com.agentmind.knowledge.outbox.model.KnowledgeIndexOutboxOperation;
import com.agentmind.knowledge.outbox.repository.KnowledgeIndexOutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Gauge;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 知识索引事务消息消费者。
 *
 * <p>数据库租约允许多个实例并行工作；失败消息按指数退避重试，超过上限进入死信。
 * 同一批次仅合并互不重复的文档，避免同一文档的连续版本被乱序覆盖。</p>
 */
@Component
@ConditionalOnProperty(prefix = "agentmind.knowledge-index.outbox", name = "enabled", havingValue = "true")
public class KnowledgeIndexOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexOutboxWorker.class);

    private final KnowledgeIndexOutboxRepository repository;
    private final KeywordIndex keywordIndex;
    private final KnowledgeIndexOutboxProperties properties;
    private final MeterRegistry meterRegistry;
    private final String leaseOwner;

    public KnowledgeIndexOutboxWorker(
            KnowledgeIndexOutboxRepository repository,
            KeywordIndex keywordIndex,
            KnowledgeIndexOutboxProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.repository = repository;
        this.keywordIndex = keywordIndex;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.leaseOwner = properties.getInstanceId().isBlank() ? createInstanceId() : properties.getInstanceId();
        registerBacklogGauges();
    }

    @Scheduled(
            fixedDelayString = "${agentmind.knowledge-index.outbox.fixed-delay-millis:1000}",
            initialDelayString = "${agentmind.knowledge-index.outbox.initial-delay-millis:3000}"
    )
    public void scheduledProcess() {
        processOnce();
    }

    /** 公开单轮处理能力，供运维触发、集成测试和故障恢复验证使用。 */
    public int processOnce() {
        return processOnce(null);
    }

    /** 只处理指定知识空间，供具备空间权限的人工恢复接口使用。 */
    public int processOnce(Long workspaceId) {
        OffsetDateTime now = OffsetDateTime.now();
        List<KnowledgeIndexOutboxEvent> claimed = repository.claimBatch(
                workspaceId, leaseOwner, now, now.plus(properties.getLeaseDuration()), properties.getBatchSize());
        meterRegistry.counter("agentmind.knowledge.index.outbox.claimed").increment(claimed.size());
        int index = 0;
        while (index < claimed.size()) {
            KnowledgeIndexOutboxEvent event = claimed.get(index);
            if (event.operation() == KnowledgeIndexOutboxOperation.DELETE) {
                processDelete(event);
                index++;
                continue;
            }
            List<KnowledgeIndexOutboxEvent> batch = collectSafeUpsertBatch(claimed, index);
            processUpsertBatch(batch);
            index += batch.size();
        }
        return claimed.size();
    }

    private List<KnowledgeIndexOutboxEvent> collectSafeUpsertBatch(
            List<KnowledgeIndexOutboxEvent> events,
            int start
    ) {
        List<KnowledgeIndexOutboxEvent> batch = new ArrayList<>();
        Set<String> documentScopes = new HashSet<>();
        for (int index = start; index < events.size(); index++) {
            KnowledgeIndexOutboxEvent event = events.get(index);
            String scope = event.workspaceId() + ":" + event.documentId();
            if (event.operation() != KnowledgeIndexOutboxOperation.UPSERT || !documentScopes.add(scope)) {
                break;
            }
            batch.add(event);
        }
        return batch;
    }

    private void processUpsertBatch(List<KnowledgeIndexOutboxEvent> events) {
        try {
            keywordIndex.replaceDocuments(events.stream().map(event -> new KeywordIndexDocument(
                    event.workspaceId(), event.documentId(), event.payload().chunks())).toList());
            events.forEach(this::complete);
        } catch (RuntimeException exception) {
            events.forEach(event -> fail(event, exception));
        }
    }

    private void processDelete(KnowledgeIndexOutboxEvent event) {
        try {
            keywordIndex.deleteDocumentChunks(event.workspaceId(), event.documentId());
            complete(event);
        } catch (RuntimeException exception) {
            fail(event, exception);
        }
    }

    private void complete(KnowledgeIndexOutboxEvent event) {
        if (repository.markCompleted(event.id(), leaseOwner, OffsetDateTime.now())) {
            meterRegistry.counter("agentmind.knowledge.index.outbox.completed").increment();
        }
    }

    private void fail(KnowledgeIndexOutboxEvent event, RuntimeException exception) {
        boolean dead = event.attempts() >= properties.getMaximumAttempts();
        OffsetDateTime now = OffsetDateTime.now();
        Duration delay = retryDelay(event.attempts());
        if (repository.markFailed(event.id(), leaseOwner, exception.getMessage(), now.plus(delay), dead, now)) {
            meterRegistry.counter("agentmind.knowledge.index.outbox.failed", "result", dead ? "dead" : "retry")
                    .increment();
        }
        log.warn("知识索引事务消息处理失败，消息={}，次数={}，是否死信={}", event.id(), event.attempts(), dead, exception);
    }

    private Duration retryDelay(int attempts) {
        long multiplier = 1L << Math.min(Math.max(0, attempts - 1), 20);
        Duration calculated = properties.getBaseRetryDelay().multipliedBy(multiplier);
        return calculated.compareTo(properties.getMaximumRetryDelay()) > 0
                ? properties.getMaximumRetryDelay() : calculated;
    }

    private String createInstanceId() {
        try {
            return InetAddress.getLocalHost().getHostName() + ":" + ManagementFactory.getRuntimeMXBean().getName()
                    + ":" + UUID.randomUUID();
        } catch (Exception exception) {
            return "unknown:" + UUID.randomUUID();
        }
    }

    /** 积压量使用仪表盘实时读取数据库，不在 JVM 内缓存可能过期的值。 */
    private void registerBacklogGauges() {
        Gauge.builder("agentmind.knowledge.index.outbox.pending", repository,
                        value -> value.statistics(null).pending())
                .description("等待处理的知识索引事务消息数量")
                .register(meterRegistry);
        Gauge.builder("agentmind.knowledge.index.outbox.retry", repository,
                        value -> value.statistics(null).retrying())
                .description("等待重试的知识索引事务消息数量")
                .register(meterRegistry);
        Gauge.builder("agentmind.knowledge.index.outbox.dead", repository,
                        value -> value.statistics(null).dead())
                .description("知识索引死信数量")
                .register(meterRegistry);
    }
}
