package com.agentmind.study.flashcard.service;

import com.agentmind.study.flashcard.config.FlashcardWebSupplementProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * 使用可配置的搜索服务为复习卡片补充外部公开资料。
 *
 * <p>当前适配 Brave Search API。外部结果只作为“联网补充”显示，不会覆盖用户资料中的结论；
 * 搜索不可用时返回清晰状态，不伪造已经联网的内容。</p>
 */
@Service
public class FlashcardWebSupplementService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlashcardWebSupplementService.class);
    private static final int MAX_SUPPLEMENT_LENGTH = 420;

    private final FlashcardWebSupplementProperties properties;

    public FlashcardWebSupplementService(FlashcardWebSupplementProperties properties) {
        this.properties = properties;
    }

    public String supplement(String question) {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getApiKey())) {
            return "当前未配置联网搜索，本卡仅展示知识库资料结论。";
        }
        try {
            JsonNode body = RestClient.builder().baseUrl(properties.getBaseUrl()).build().get()
                    .uri(builder -> builder.path("/res/v1/web/search")
                            .queryParam("q", question)
                            .queryParam("count", Math.max(1, Math.min(5, properties.getResultCount())))
                            .queryParam("search_lang", "zh-hans")
                            .build())
                    .header("X-Subscription-Token", properties.getApiKey())
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .retrieve()
                    .body(JsonNode.class);
            return summarize(body);
        } catch (RuntimeException exception) {
            LOGGER.warn("复习卡片联网补充失败：{}", exception.getMessage());
            return "联网搜索暂时不可用，本卡保留知识库资料结论。";
        }
    }

    private String summarize(JsonNode body) {
        JsonNode results = body == null ? null : body.path("web").path("results");
        if (results == null || !results.isArray() || results.isEmpty()) {
            return "未检索到可信的外部补充结果。";
        }
        List<String> snippets = new ArrayList<>();
        for (JsonNode result : results) {
            String description = result.path("description").asText("").replaceAll("\\s+", " ").trim();
            String url = result.path("url").asText("").trim();
            if (StringUtils.hasText(description)) {
                snippets.add(description + (StringUtils.hasText(url) ? "（" + url + "）" : ""));
            }
        }
        String combined = String.join("\n", snippets);
        if (!StringUtils.hasText(combined)) {
            return "未检索到可信的外部补充结果。";
        }
        return combined.length() <= MAX_SUPPLEMENT_LENGTH
                ? combined : combined.substring(0, MAX_SUPPLEMENT_LENGTH) + "…";
    }
}
