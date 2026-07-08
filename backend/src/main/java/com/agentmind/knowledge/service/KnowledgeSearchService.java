package com.agentmind.knowledge.service;

import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.agentmind.knowledge.model.VectorSearchResult;
import com.agentmind.knowledge.model.dto.KnowledgeSearchResponse;
import com.agentmind.knowledge.model.dto.KnowledgeSearchResultResponse;
import com.agentmind.knowledge.vector.EmbeddingClient;
import com.agentmind.knowledge.vector.VectorStore;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Application service for workspace-scoped semantic search.
 *
 * <p>The current implementation uses a deterministic local embedding and an in-memory vector store. The service
 * already enforces workspace scope, which is the core contract that must remain when pgvector is introduced.</p>
 */
@Service
public class KnowledgeSearchService {

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 20;

    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;

    public KnowledgeSearchService(EmbeddingClient embeddingClient, VectorStore vectorStore) {
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
    }

    public KnowledgeSearchResponse search(Long workspaceId, String query, Integer topK) {
        validateWorkspaceId(workspaceId);
        if (!StringUtils.hasText(query)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "query must not be blank");
        }
        int safeTopK = normalizeTopK(topK);
        float[] queryEmbedding = embeddingClient.embed(query);
        List<KnowledgeSearchResultResponse> results = vectorStore.search(workspaceId, queryEmbedding, safeTopK)
                .stream()
                .map(this::toResponse)
                .toList();
        return new KnowledgeSearchResponse(query, safeTopK, results);
    }

    private int normalizeTopK(Integer topK) {
        if (topK == null) {
            return DEFAULT_TOP_K;
        }
        return Math.min(Math.max(topK, 1), MAX_TOP_K);
    }

    private void validateWorkspaceId(Long workspaceId) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "workspaceId must be positive");
        }
    }

    private KnowledgeSearchResultResponse toResponse(VectorSearchResult result) {
        return new KnowledgeSearchResultResponse(
                result.chunkId(),
                result.documentId(),
                result.chunkSequence(),
                result.headingPath(),
                result.content(),
                result.score()
        );
    }
}
