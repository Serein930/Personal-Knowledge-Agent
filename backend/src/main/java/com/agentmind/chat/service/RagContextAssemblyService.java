package com.agentmind.chat.service;

import com.agentmind.chat.model.dto.RagChatRequest;
import com.agentmind.chat.model.dto.RagChatResponse;
import com.agentmind.chat.model.dto.RagCitationResponse;
import com.agentmind.chat.model.dto.RagRetrievalContextResponse;
import com.agentmind.chat.model.dto.TokenUsageResponse;
import com.agentmind.knowledge.model.dto.KnowledgeSearchResponse;
import com.agentmind.knowledge.model.dto.KnowledgeSearchResultResponse;
import com.agentmind.knowledge.service.KnowledgeSearchService;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/**
 * Assembles retrieved chunks into a RAG-ready context.
 *
 * <p>This service is the bridge between vector retrieval and future answer generation. It deliberately does not call
 * an LLM yet. Instead, it produces a deterministic context and citations so tests, frontend pages and future prompt
 * templates can agree on the contract before model integration begins.</p>
 */
@Service
public class RagContextAssemblyService {

    private static final String RETRIEVAL_ONLY_ANSWER = """
            Retrieval context has been prepared. Model answer generation is not enabled in this stage.
            """;

    private final KnowledgeSearchService knowledgeSearchService;
    private final AtomicLong messageIdGenerator = new AtomicLong(10_000);

    public RagContextAssemblyService(KnowledgeSearchService knowledgeSearchService) {
        this.knowledgeSearchService = knowledgeSearchService;
    }

    public RagChatResponse prepareChatContext(Long workspaceId, RagChatRequest request) {
        KnowledgeSearchResponse searchResponse = knowledgeSearchService.search(
                workspaceId,
                request.question(),
                request.topK()
        );
        List<RagCitationResponse> citations = toCitations(searchResponse.results());
        RagRetrievalContextResponse retrievalContext = new RagRetrievalContextResponse(
                request.question(),
                searchResponse.topK(),
                buildPromptContext(request.question(), citations),
                citations
        );

        return new RagChatResponse(
                request.conversationId(),
                messageIdGenerator.incrementAndGet(),
                RETRIEVAL_ONLY_ANSWER.strip(),
                retrievalContext,
                citations,
                List.of(),
                new TokenUsageResponse(0, 0, 0)
        );
    }

    private List<RagCitationResponse> toCitations(List<KnowledgeSearchResultResponse> results) {
        return java.util.stream.IntStream.range(0, results.size())
                .mapToObj(index -> toCitation(index + 1, results.get(index)))
                .toList();
    }

    private RagCitationResponse toCitation(int index, KnowledgeSearchResultResponse result) {
        return new RagCitationResponse(
                index,
                result.documentId(),
                "Document #" + result.documentId(),
                result.chunkId(),
                result.chunkSequence(),
                result.headingPath(),
                result.content(),
                result.score()
        );
    }

    private String buildPromptContext(String question, List<RagCitationResponse> citations) {
        StringBuilder builder = new StringBuilder();
        builder.append("Use only the following retrieved knowledge chunks to answer the question.\n");
        builder.append("If the context is insufficient, say that the current knowledge base does not contain enough evidence.\n\n");
        builder.append("Question:\n").append(question).append("\n\n");
        builder.append("Retrieved context:\n");
        for (RagCitationResponse citation : citations) {
            builder.append("[")
                    .append(citation.index())
                    .append("] documentId=")
                    .append(citation.documentId())
                    .append(", chunkId=")
                    .append(citation.chunkId())
                    .append(", heading=")
                    .append(citation.headingPath() == null ? "" : citation.headingPath())
                    .append(", score=")
                    .append(String.format(java.util.Locale.ROOT, "%.4f", citation.score()))
                    .append("\n")
                    .append(citation.excerpt())
                    .append("\n\n");
        }
        return builder.toString().trim();
    }
}
