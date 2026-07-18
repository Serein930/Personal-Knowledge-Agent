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
 * 按知识空间隔离的语义检索应用服务。
 *
 * <p>当前实现使用确定性本地向量和内存向量库。服务层已经强制按知识空间检索，
 * 这是后续切换到数据库向量扩展时必须保持的核心契约。</p>
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
        return search(workspaceId, query, topK, null);
    }

    /** 使用与文档索引一致的向量模型生成查询向量，避免混用不同向量空间。 */
    public KnowledgeSearchResponse search(Long workspaceId, String query, Integer topK, String embeddingModel) {
        validateWorkspaceId(workspaceId);
        if (!StringUtils.hasText(query)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "查询内容不能为空");
        }
        int safeTopK = normalizeTopK(topK);
        float[] queryEmbedding = StringUtils.hasText(embeddingModel)
                ? embeddingClient.embed(query, embeddingModel)
                : embeddingClient.embed(query);
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
            throw new BusinessException(ErrorCode.BAD_REQUEST, "知识空间ID必须为正数");
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
