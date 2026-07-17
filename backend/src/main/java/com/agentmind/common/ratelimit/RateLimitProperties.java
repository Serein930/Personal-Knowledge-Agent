package com.agentmind.common.ratelimit;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Redis 分布式限流、接口配额和故障策略配置。 */
@Validated
@ConfigurationProperties(prefix = "agentmind.rate-limit")
public class RateLimitProperties {

    private RateLimitMode mode = RateLimitMode.DISABLED;
    private String keyPrefix = "agentmind:rate-limit";
    private Duration window = Duration.ofMinutes(1);
    private boolean failOpen;
    private String clientIpHeader = "";

    @Min(value = 1, message = "通用接口配额必须大于零")
    private int generalRequests = 300;

    @Min(value = 1, message = "认证接口配额必须大于零")
    private int authenticationRequests = 20;

    @Min(value = 1, message = "摄取接口配额必须大于零")
    private int ingestionRequests = 60;

    @Min(value = 1, message = "检索增强生成接口配额必须大于零")
    private int ragRequests = 60;

    public RateLimitMode getMode() {
        return mode;
    }

    public void setMode(RateLimitMode mode) {
        this.mode = mode;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Duration getWindow() {
        return window;
    }

    public void setWindow(Duration window) {
        this.window = window;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    public String getClientIpHeader() {
        return clientIpHeader;
    }

    public void setClientIpHeader(String clientIpHeader) {
        this.clientIpHeader = clientIpHeader;
    }

    public int getGeneralRequests() {
        return generalRequests;
    }

    public void setGeneralRequests(int generalRequests) {
        this.generalRequests = generalRequests;
    }

    public int getAuthenticationRequests() {
        return authenticationRequests;
    }

    public void setAuthenticationRequests(int authenticationRequests) {
        this.authenticationRequests = authenticationRequests;
    }

    public int getIngestionRequests() {
        return ingestionRequests;
    }

    public void setIngestionRequests(int ingestionRequests) {
        this.ingestionRequests = ingestionRequests;
    }

    public int getRagRequests() {
        return ragRequests;
    }

    public void setRagRequests(int ragRequests) {
        this.ragRequests = ragRequests;
    }

    public int limitFor(RateLimitScope scope) {
        return switch (scope) {
            case AUTHENTICATION -> authenticationRequests;
            case INGESTION -> ingestionRequests;
            case RAG -> ragRequests;
            case GENERAL -> generalRequests;
        };
    }

    @AssertTrue(message = "限流时间窗口必须大于零")
    public boolean isWindowValid() {
        return window != null && !window.isZero() && !window.isNegative();
    }
}
