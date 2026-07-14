package com.agentmind.agent.confirmation.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 写工具确认单后台维护配置。
 */
@Component
@ConfigurationProperties(prefix = "agentmind.agent.confirmation-maintenance")
public class AgentToolConfirmationMaintenanceProperties {

    private boolean enabled = true;
    private long fixedDelayMillis = 60_000;
    private long initialDelayMillis = 60_000;
    private Duration executingTimeout = Duration.ofMinutes(10);
    private int batchSize = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

    public Duration getExecutingTimeout() {
        return executingTimeout;
    }

    public void setExecutingTimeout(Duration executingTimeout) {
        this.executingTimeout = executingTimeout;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
