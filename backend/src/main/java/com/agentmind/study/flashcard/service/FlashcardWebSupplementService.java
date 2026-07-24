package com.agentmind.study.flashcard.service;

import com.agentmind.study.flashcard.config.FlashcardWebSupplementProperties;
import com.agentmind.study.flashcard.search.FlashcardWebSearchClient;
import com.agentmind.study.flashcard.search.FlashcardWebSearchProvider;
import com.agentmind.study.flashcard.search.FlashcardWebSearchResult;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 使用可配置的搜索服务为复习卡片补充外部公开资料。
 *
 * <p>外部结果只作为“联网补充”显示，不会覆盖用户资料中的结论。该服务只依赖统一搜索端口，
 * 不感知 Brave 或 SearXNG 的具体响应结构；搜索不可用时返回清晰状态，不伪造联网内容。</p>
 */
@Service
public class FlashcardWebSupplementService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlashcardWebSupplementService.class);
    private static final int MAX_SUPPLEMENT_LENGTH = 420;

    private final FlashcardWebSupplementProperties properties;
    private final Map<FlashcardWebSearchProvider, FlashcardWebSearchClient> clients;

    @Autowired
    public FlashcardWebSupplementService(
            FlashcardWebSupplementProperties properties,
            List<FlashcardWebSearchClient> clients
    ) {
        this.properties = properties;
        this.clients = new EnumMap<>(FlashcardWebSearchProvider.class);
        clients.forEach(client -> this.clients.put(client.provider(), client));
    }

    /** 供离线单元测试构造默认关闭的服务，避免任何真实网络访问。 */
    public FlashcardWebSupplementService(FlashcardWebSupplementProperties properties) {
        this(properties, List.of());
    }

    public String supplement(String question) {
        if (!properties.isEnabled()) {
            return "当前未配置联网搜索，本卡仅展示知识库资料结论。";
        }
        FlashcardWebSearchClient client = clients.get(properties.getProvider());
        if (client == null) {
            return "当前搜索提供方没有可用适配器，本卡仅展示知识库资料结论。";
        }
        if (!client.isConfigured()) {
            return "当前搜索提供方配置不完整，本卡仅展示知识库资料结论。";
        }
        try {
            return summarize(client.search(question, properties.getResultCount()));
        } catch (RuntimeException exception) {
            LOGGER.warn("复习卡片联网补充失败：提供方={}，原因={}",
                    properties.getProvider(), exception.getMessage());
            return "联网搜索暂时不可用，本卡保留知识库资料结论。";
        }
    }

    private String summarize(List<FlashcardWebSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "未检索到可信的外部补充结果。";
        }
        List<String> snippets = new ArrayList<>();
        for (int index = 0; index < results.size(); index++) {
            FlashcardWebSearchResult result = results.get(index);
            String title = clean(result.title());
            String description = clean(result.snippet());
            String url = result.url() == null ? "" : result.url().trim();
            if (StringUtils.hasText(description)) {
                String heading = StringUtils.hasText(title) ? title + "：" : "";
                String source = StringUtils.hasText(url) ? "（来源：" + url + "）" : "";
                snippets.add((index + 1) + ". " + heading + description + source);
            }
        }
        String combined = String.join("\n", snippets);
        if (!StringUtils.hasText(combined)) {
            return "未检索到可信的外部补充结果。";
        }
        return combined.length() <= MAX_SUPPLEMENT_LENGTH
                ? combined : combined.substring(0, MAX_SUPPLEMENT_LENGTH) + "…";
    }

    private String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return Jsoup.parse(value).text().replaceAll("\\s+", " ").trim();
    }
}
