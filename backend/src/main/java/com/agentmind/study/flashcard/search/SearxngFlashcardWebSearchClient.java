package com.agentmind.study.flashcard.search;

import com.agentmind.study.flashcard.config.FlashcardWebSupplementProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * 自建 SearXNG 搜索协议适配器。
 *
 * <p>SearXNG 的 JSON 接口必须在服务端 {@code settings.yml} 中显式启用。本适配器不需要
 * API Key，只访问用户配置的受信任实例，并在客户端限制结果数量，避免把整页结果写入复习卡片。</p>
 */
@Component
public class SearxngFlashcardWebSearchClient implements FlashcardWebSearchClient {

    private final FlashcardWebSupplementProperties properties;
    private final RestClient restClient;

    @Autowired
    public SearxngFlashcardWebSearchClient(FlashcardWebSupplementProperties properties) {
        this(properties, RestClient.builder().baseUrl(properties.getBaseUrl()).build());
    }

    SearxngFlashcardWebSearchClient(
            FlashcardWebSupplementProperties properties,
            RestClient restClient
    ) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public FlashcardWebSearchProvider provider() {
        return FlashcardWebSearchProvider.SEARXNG;
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(properties.getBaseUrl());
    }

    @Override
    public List<FlashcardWebSearchResult> search(String query, int resultCount) {
        JsonNode body = restClient.get()
                .uri(builder -> builder.path("/search")
                        .queryParam("q", query)
                        .queryParam("format", "json")
                        .queryParam("language", "zh-CN")
                        .queryParam("safesearch", 1)
                        .build())
                .header(HttpHeaders.ACCEPT, "application/json")
                .retrieve()
                .body(JsonNode.class);
        JsonNode results = body == null ? null : body.path("results");
        if (results == null || !results.isArray()) {
            return List.of();
        }
        int limit = Math.max(1, Math.min(5, resultCount));
        List<FlashcardWebSearchResult> mapped = new ArrayList<>();
        for (JsonNode result : results) {
            if (mapped.size() >= limit) {
                break;
            }
            mapped.add(new FlashcardWebSearchResult(
                    result.path("title").asText(""),
                    result.path("content").asText(""),
                    result.path("url").asText("")
            ));
        }
        return List.copyOf(mapped);
    }
}
