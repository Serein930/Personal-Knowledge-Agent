package com.agentmind.knowledge.service;

import com.agentmind.knowledge.model.dto.KnowledgeSearchResultResponse;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 基于名次的倒数排序融合算法。
 *
 * <p>RRF 不直接比较向量余弦分和 BM25 分，避免两种分数尺度不同导致某一路长期压制另一路。
 * 同一片段在两个榜单中出现时累加贡献，单路命中仍可以进入最终候选。</p>
 */
@Component
public class ReciprocalRankFusion {

    public List<KnowledgeSearchResultResponse> fuse(
            List<KnowledgeSearchResultResponse> vectorResults,
            List<KnowledgeSearchResultResponse> keywordResults,
            int rankConstant,
            int limit
    ) {
        Map<String, FusedCandidate> candidates = new LinkedHashMap<>();
        addRanking(candidates, vectorResults, rankConstant);
        addRanking(candidates, keywordResults, rankConstant);
        double maximumPossibleScore = 2.0 / (rankConstant + 1.0);
        return candidates.values().stream()
                .sorted(Comparator.comparingDouble(FusedCandidate::score).reversed()
                        .thenComparing(value -> value.result().chunkId()))
                .limit(limit)
                .map(value -> withScore(value.result(), value.score() / maximumPossibleScore))
                .toList();
    }

    private void addRanking(
            Map<String, FusedCandidate> candidates,
            List<KnowledgeSearchResultResponse> ranking,
            int rankConstant
    ) {
        for (int index = 0; index < ranking.size(); index++) {
            KnowledgeSearchResultResponse result = ranking.get(index);
            double contribution = 1.0 / (rankConstant + index + 1.0);
            candidates.compute(result.chunkId(), (ignored, existing) -> existing == null
                    ? new FusedCandidate(result, contribution)
                    : new FusedCandidate(existing.result(), existing.score() + contribution));
        }
    }

    private KnowledgeSearchResultResponse withScore(KnowledgeSearchResultResponse result, double score) {
        return new KnowledgeSearchResultResponse(
                result.chunkId(), result.documentId(), result.chunkSequence(), result.headingPath(),
                result.content(), Math.min(1.0, score)
        );
    }

    private record FusedCandidate(KnowledgeSearchResultResponse result, double score) {
    }
}
