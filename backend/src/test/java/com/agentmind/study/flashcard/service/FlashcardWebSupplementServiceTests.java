package com.agentmind.study.flashcard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.study.flashcard.config.FlashcardWebSupplementProperties;
import com.agentmind.study.flashcard.search.FlashcardWebSearchClient;
import com.agentmind.study.flashcard.search.FlashcardWebSearchProvider;
import com.agentmind.study.flashcard.search.FlashcardWebSearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 验证联网补充服务按提供方路由，并清理搜索摘要中的网页标签。 */
class FlashcardWebSupplementServiceTests {

    @Test
    void shouldUseSearxngWithoutApiKeyAndNormalizeResult() {
        FlashcardWebSupplementProperties properties = new FlashcardWebSupplementProperties();
        properties.setEnabled(true);
        properties.setProvider(FlashcardWebSearchProvider.SEARXNG);
        properties.setApiKey("");
        FlashcardWebSearchClient client = new StubSearchClient();
        FlashcardWebSupplementService service =
                new FlashcardWebSupplementService(properties, List.of(client));

        String supplement = service.supplement("什么是虚拟线程");

        assertThat(supplement)
                .contains("1. Java 虚拟线程：轻量级并发执行单元")
                .contains("来源：https://example.com/virtual-thread")
                .doesNotContain("<b>");
    }

    private static final class StubSearchClient implements FlashcardWebSearchClient {

        @Override
        public FlashcardWebSearchProvider provider() {
            return FlashcardWebSearchProvider.SEARXNG;
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public List<FlashcardWebSearchResult> search(String query, int resultCount) {
            return List.of(new FlashcardWebSearchResult(
                    "Java 虚拟线程",
                    "<b>轻量级</b>并发执行单元",
                    "https://example.com/virtual-thread"
            ));
        }
    }
}
