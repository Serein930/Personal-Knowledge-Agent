package com.agentmind.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.document.chunk.DocumentChunk;
import com.agentmind.knowledge.model.dto.KnowledgeSearchResponse;
import com.agentmind.knowledge.vector.DeterministicEmbeddingClient;
import com.agentmind.knowledge.vector.InMemoryVectorStore;
import com.agentmind.knowledge.keyword.InMemoryBm25KeywordIndex;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 测试数据库向量扩展接入前的本地语义检索骨架。
 */
class KnowledgeSearchServiceTests {

    private final DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();
    private final InMemoryVectorStore vectorStore = new InMemoryVectorStore();
    private final KnowledgeIndexingService indexingService = new KnowledgeIndexingService(
            embeddingClient, vectorStore, new InMemoryBm25KeywordIndex()
    );
    private final KnowledgeSearchService searchService = new KnowledgeSearchService(embeddingClient, vectorStore);

    @Test
    void searchShouldReturnOnlyChunksFromCurrentWorkspace() {
        indexingService.indexChunks(1L, 10L, List.of(
                new DocumentChunk("10-0", 10L, 0, "Java",
                        "Thread pools reuse worker threads for backend tasks.", 0, 54)
        ));
        indexingService.indexChunks(2L, 20L, List.of(
                new DocumentChunk("20-0", 20L, 0, "Java",
                        "Thread pools in another workspace should stay isolated.", 0, 58)
        ));

        KnowledgeSearchResponse response = searchService.search(1L, "worker threads backend", 5);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().getFirst().documentId()).isEqualTo(10L);
        assertThat(response.results().getFirst().content()).contains("backend tasks");
    }
}
