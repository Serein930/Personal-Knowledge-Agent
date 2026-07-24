package com.agentmind.study.flashcard.config;

import com.agentmind.study.flashcard.search.FlashcardWebSearchProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 复习卡片联网补充配置。
 *
 * <p>联网能力默认关闭，避免测试与离线开发产生外部请求。启用后可以选择自建 SearXNG，
 * 也可以继续使用需要密钥的 Brave Search。基础地址只属于当前选中的提供方，切换提供方时
 * 必须同时检查地址是否匹配。</p>
 */
@Component
@ConfigurationProperties(prefix = "agentmind.study.flashcard.web-supplement")
public class FlashcardWebSupplementProperties {

    private boolean enabled;
    private FlashcardWebSearchProvider provider = FlashcardWebSearchProvider.SEARXNG;
    private String baseUrl = "http://localhost:8888";
    private String apiKey;
    private int resultCount = 3;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public FlashcardWebSearchProvider getProvider() {
        return provider;
    }

    public void setProvider(FlashcardWebSearchProvider provider) {
        this.provider = provider;
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
