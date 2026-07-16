package com.agentmind.knowledge.outbox.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 知识索引事务消息的领取、重试和重建参数。 */
@Component
@ConfigurationProperties(prefix = "agentmind.knowledge-index.outbox")
public class KnowledgeIndexOutboxProperties {

    private boolean enabled;
    private String instanceId = "";
    private int batchSize = 50;
    private Duration leaseDuration = Duration.ofSeconds(30);
    private Duration baseRetryDelay = Duration.ofSeconds(2);
    private Duration maximumRetryDelay = Duration.ofMinutes(5);
    private int maximumAttempts = 8;
    private long fixedDelayMillis = 1_000;
    private long initialDelayMillis = 3_000;
    private int rebuildDocumentBatchSize = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public Duration getBaseRetryDelay() {
        return baseRetryDelay;
    }

    public void setBaseRetryDelay(Duration baseRetryDelay) {
        this.baseRetryDelay = baseRetryDelay;
    }

    public Duration getMaximumRetryDelay() {
        return maximumRetryDelay;
    }

    public void setMaximumRetryDelay(Duration maximumRetryDelay) {
        this.maximumRetryDelay = maximumRetryDelay;
    }

    public int getMaximumAttempts() {
        return maximumAttempts;
    }

    public void setMaximumAttempts(int maximumAttempts) {
        this.maximumAttempts = maximumAttempts;
    }

    public long getFixedDelayMillis() {
        return fixedDelayMillis;
    }

    public void setFixedDelayMillis(long fixedDelayMillis) {
        this.fixedDelayMillis = fixedDelayMillis;
    }

    public long getInitialDelayMillis() {
        return initialDelayMillis;
    }

    public void setInitialDelayMillis(long initialDelayMillis) {
        this.initialDelayMillis = initialDelayMillis;
    }

    public int getRebuildDocumentBatchSize() {
        return rebuildDocumentBatchSize;
    }

    public void setRebuildDocumentBatchSize(int rebuildDocumentBatchSize) {
        this.rebuildDocumentBatchSize = rebuildDocumentBatchSize;
    }
}
