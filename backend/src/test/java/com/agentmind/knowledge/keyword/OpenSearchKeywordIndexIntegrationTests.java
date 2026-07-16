package com.agentmind.knowledge.keyword;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.document.chunk.DocumentChunk;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** 需要显式启动 Docker OpenSearch 后才运行的真实 BM25 联调测试。 */
@EnabledIfEnvironmentVariable(named = "AGENTMIND_RUN_OPENSEARCH_INTEGRATION", matches = "true")
class OpenSearchKeywordIndexIntegrationTests {

    @Test
    void shouldIndexSearchAndDeleteWorkspaceDocument() {
        OpenSearchKeywordIndexProperties properties = new OpenSearchKeywordIndexProperties();
        properties.setBaseUrl(System.getenv().getOrDefault("AGENTMIND_OPENSEARCH_URL", "http://localhost:9200"));
        properties.setIndexName("agentmind-integration-test-chunks");
        OpenSearchKeywordIndex index = new OpenSearchKeywordIndex(properties);
        long workspaceId = ThreadLocalRandom.current().nextLong(100_000, 999_999);
        DocumentChunk chunk = new DocumentChunk(
                "opensearch-test-" + workspaceId, 9001L, 0, "并发测试",
                "结构化并发使用任务作用域管理子任务生命周期。", 0, 24
        );

        index.replaceDocumentChunks(workspaceId, 9001L, List.of(chunk));
        assertThat(index.search(workspaceId, "任务作用域", 5))
                .extracting(value -> value.chunkId()).contains(chunk.id());

        index.deleteDocumentChunks(workspaceId, 9001L);
        assertThat(index.search(workspaceId, "任务作用域", 5)).isEmpty();
    }
}
