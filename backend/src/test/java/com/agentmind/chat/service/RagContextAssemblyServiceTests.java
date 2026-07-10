package com.agentmind.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmind.chat.model.dto.RagChatRequest;
import com.agentmind.chat.model.dto.RagChatResponse;
import com.agentmind.document.chunk.DocumentChunk;
import com.agentmind.knowledge.service.KnowledgeIndexingService;
import com.agentmind.knowledge.service.KnowledgeSearchService;
import com.agentmind.knowledge.vector.DeterministicEmbeddingClient;
import com.agentmind.knowledge.vector.InMemoryVectorStore;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagContextAssemblyServiceTests {

    private final DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();
    private final InMemoryVectorStore vectorStore = new InMemoryVectorStore();
    private final KnowledgeIndexingService indexingService = new KnowledgeIndexingService(embeddingClient, vectorStore);
    private final RagContextAssemblyService service = new RagContextAssemblyService(
            new KnowledgeSearchService(embeddingClient, vectorStore),
            new MockAnswerGenerator()
    );

    @Test
    void prepareChatContextShouldGenerateMockAnswerFromCitations() {
        indexingService.indexChunks(1L, 100L, List.of(
                new DocumentChunk(
                        "100-0",
                        100L,
                        0,
                        "Thread Pool",
                        "Thread pools reuse backend worker threads and reduce task creation overhead.",
                        0,
                        78
                )
        ));

        RagChatResponse response = service.prepareChatContext(
                1L,
                new RagChatRequest(null, "How do thread pools help backend tasks?", 3, null)
        );

        assertThat(response.answer()).contains("Based on the retrieved knowledge");
        assertThat(response.answer()).contains("Thread pools reuse backend worker threads");
        assertThat(response.answer()).contains("[1]");
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().getFirst().chunkId()).isEqualTo("100-0");
        assertThat(response.retrievalContext().promptContext()).contains("Question:");
        assertThat(response.retrievalContext().promptContext()).contains("documentId=100");
        assertThat(response.retrievalContext().promptContext()).contains("chunkId=100-0");
        assertThat(response.usage().totalTokens()).isZero();
    }

    @Test
    void prepareChatContextShouldReturnInsufficientContextAnswerWhenNoCitationMatches() {
        RagChatResponse response = service.prepareChatContext(
                1L,
                new RagChatRequest(null, "What does the knowledge base say about an unknown topic?", 3, null)
        );

        assertThat(response.answer()).contains("does not contain enough retrieved context");
        assertThat(response.citations()).isEmpty();
        assertThat(response.retrievalContext().promptContext()).contains("Question:");
        assertThat(response.usage().totalTokens()).isZero();
    }
}
