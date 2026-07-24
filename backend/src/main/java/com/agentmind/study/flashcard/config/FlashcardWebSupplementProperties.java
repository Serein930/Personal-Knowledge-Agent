package com.agentmind.study.flashcard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 复习卡片联网补充配置，默认关闭，避免测试与离线开发产生外部请求。 */
@Component
@ConfigurationProperties(prefix = "agentmind.study.flashcard.web-supplement")
public class FlashcardWebSupplementProperties {

    private boolean enabled;
    private String baseUrl = "https://api.search.brave.com";
    private String apiKey;
    private int resultCount = 3;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getResultCount() {
        return resultCount;
    }

    public void setResultCount(int resultCount) {
        this.resultCount = resultCount;
    }
}
