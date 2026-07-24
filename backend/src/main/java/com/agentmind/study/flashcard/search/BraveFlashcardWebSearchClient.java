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

/** Brave Search 协议适配器，保留已有付费搜索配置的兼容能力。 */
@Component
public class BraveFlashcardWebSearchClient implements FlashcardWebSearchClient {

    private final FlashcardWebSupplementProperties properties;
    private final RestClient restClient;

    @Autowired
    public BraveFlashcardWebSearchClient(FlashcardWebSupplementProperties properties) {
        this(properties, RestClient.builder().baseUrl(properties.getBaseUrl()).build());
    }

    BraveFlashcardWebSearchClient(
            FlashcardWebSupplementProperties properties,
            RestClient restClient
    ) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public FlashcardWebSearchProvider provider() {
        return FlashcardWebSearchProvider.BRAVE;
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(properties.getApiKey());
    }

    @Override
    public List<FlashcardWebSearchResult> search(String query, int resultCount) {
        JsonNode body = restClient.get()
                .uri(builder -> builder.path("/res/v1/web/search")
                        .queryParam("q", query)
                        .queryParam("count", boundedCount(resultCount))
                        .queryParam("search_lang", "zh-hans")
                        .build())
                .header("X-Subscription-Token", properties.getApiKey())
                .header(HttpHeaders.ACCEPT, "application/json")
                .retrieve()
                .body(JsonNode.class);
        JsonNode results = body == null ? null : body.path("web").path("results");
        if (results == null || !results.isArray()) {
            return List.of();
        }
        List<FlashcardWebSearchResult> mapped = new ArrayList<>();
        for (JsonNode result : results) {
            mapped.add(new FlashcardWebSearchResult(
                    result.path("title").asText(""),
                    result.path("description").asText(""),
                    result.path("url").asText("")
            ));
        }
        return List.copyOf(mapped);
    }

    private int boundedCount(int requestedCount) {
        return Math.max(1, Math.min(5, requestedCount));
    }
}
