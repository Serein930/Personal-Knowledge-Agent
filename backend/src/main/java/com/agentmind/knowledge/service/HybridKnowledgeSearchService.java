package com.agentmind.knowledge.service;

import com.agentmind.evaluation.config.RagEvaluationProperties;
import com.agentmind.knowledge.keyword.KeywordIndex;
import com.agentmind.knowledge.model.dto.KnowledgeSearchResultResponse;
import java.util.List;
import org.springframework.stereotype.Service;

/** 组合向量召回、BM25 召回与 RRF 融合的混合检索应用服务。 */
@Service
public class HybridKnowledgeSearchService {

    private final KnowledgeSearchService vectorSearchService;
    private final KeywordIndex keywordIndex;
    private final ReciprocalRankFusion fusion;
    private final RagEvaluationProperties evaluationProperties;

    public HybridKnowledgeSearchService(
            KnowledgeSearchService vectorSearchService,
            KeywordIndex keywordIndex,
            ReciprocalRankFusion fusion,
            RagEvaluationProperties evaluationProperties
    ) {
        this.vectorSearchService = vectorSearchService;
        this.keywordIndex = keywordIndex;
        this.fusion = fusion;
        this.evaluationProperties = evaluationProperties;
    }

    public List<KnowledgeSearchResultResponse> search(Long workspaceId, String query, int candidatePoolSize) {
        List<KnowledgeSearchResultResponse> vectorResults = vectorSearchService
                .search(workspaceId, query, candidatePoolSize).results();
        List<KnowledgeSearchResultResponse> keywordResults = keywordIndex.search(
                workspaceId, query, candidatePoolSize
        );
        return fusion.fuse(
                vectorResults,
                keywordResults,
                Math.max(1, evaluationProperties.getRrfRankConstant()),
                candidatePoolSize
        );
    }
}
