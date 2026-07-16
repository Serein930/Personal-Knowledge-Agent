package com.agentmind.evaluation.service;

import com.agentmind.evaluation.metric.RagEvaluationTextSimilarity;
import com.agentmind.evaluation.model.RagEvaluationExperimentConfig;
import com.agentmind.evaluation.model.RagEvaluationRerankStrategy;
import com.agentmind.evaluation.model.RagEvaluationRetrievalStrategy;
import com.agentmind.knowledge.model.dto.KnowledgeSearchResultResponse;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/**
 * 评估专用候选融合和重排器。
 *
 * <p>向量召回仍由正式知识检索端口完成。本组件只对候选集合重新计分，避免评估代码绕过知识空间过滤。
 * HYBRID 按“70%向量分 + 30%词法分”融合，LEXICAL 重排再提高词法权重，所有权重随代码版本固定。</p>
 */
@Component
public class RagEvaluationCandidateRanker {

    private final RagEvaluationTextSimilarity textSimilarity;

    public RagEvaluationCandidateRanker(RagEvaluationTextSimilarity textSimilarity) {
        this.textSimilarity = textSimilarity;
    }

    public List<KnowledgeSearchResultResponse> rank(
            String question,
            List<KnowledgeSearchResultResponse> candidates,
            RagEvaluationExperimentConfig config
    ) {
        List<ScoredCandidate> scored = IntStream.range(0, candidates.size()).mapToObj(index -> {
            KnowledgeSearchResultResponse candidate = candidates.get(index);
            double lexicalScore = textSimilarity.similarity(question, candidate.content());
            double vectorScore = clamp(candidate.score());
            double retrievalScore = config.retrievalStrategy() == RagEvaluationRetrievalStrategy.HYBRID
                    ? vectorScore * 0.70 + lexicalScore * 0.30
                    : vectorScore;
            double finalScore = config.rerankStrategy() == RagEvaluationRerankStrategy.LEXICAL
                    ? retrievalScore * 0.55 + lexicalScore * 0.45
                    : retrievalScore;
            return new ScoredCandidate(candidate, finalScore, index);
        }).toList();
        return scored.stream()
                .sorted(Comparator.comparingDouble(ScoredCandidate::score).reversed()
                        .thenComparingInt(ScoredCandidate::originalIndex))
                .limit(config.topK())
                .map(ScoredCandidate::candidate)
                .toList();
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private record ScoredCandidate(
            KnowledgeSearchResultResponse candidate,
            double score,
            int originalIndex
    ) {
    }
}
