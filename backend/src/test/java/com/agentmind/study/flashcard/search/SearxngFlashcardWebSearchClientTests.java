package com.agentmind.study.flashcard.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.agentmind.study.flashcard.config.FlashcardWebSupplementProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 验证 SearXNG JSON 协议解析和客户端结果数量限制。 */
class SearxngFlashcardWebSearchClientTests {

    @Test
    void shouldParseSearxngJsonWithoutApiKey() {
        FlashcardWebSupplementProperties properties = new FlashcardWebSupplementProperties();
        properties.setBaseUrl("http://localhost:8888");
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SearxngFlashcardWebSearchClient client =
                new SearxngFlashcardWebSearchClient(properties, builder.build());

        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/search");
                    assertThat(request.getURI().getQuery())
                            .contains("format=json")
                            .contains("safesearch=1");
                })
                .andRespond(withSuccess("""
                        {
                          "results": [
                            {"title":"结果一","content":"第一条摘要","url":"https://example.com/1"},
                            {"title":"结果二","content":"第二条摘要","url":"https://example.com/2"},
                            {"title":"结果三","content":"第三条摘要","url":"https://example.com/3"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var results = client.search("Java 虚拟线程", 2);

        assertThat(client.isConfigured()).isTrue();
        assertThat(results).hasSize(2);
        assertThat(results.getFirst().title()).isEqualTo("结果一");
        assertThat(results.getFirst().snippet()).isEqualTo("第一条摘要");
        server.verify();
    }
}
