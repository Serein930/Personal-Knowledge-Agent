package com.agentmind.evaluation.service;

import com.agentmind.evaluation.metric.RagEvaluationTextSimilarity;
import com.agentmind.evaluation.model.RagEvaluationExperimentConfig;
import com.agentmind.evaluation.model.RagEvaluationRerankStrategy;
import com.agentmind.knowledge.model.dto.KnowledgeSearchResultResponse;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/**
 * 评估专用候选融合和重排器。
 *
 * <p>向量或 RRF 双路融合已由正式知识检索服务完成。本组件只执行可选的词法重排，
 * 避免把候选召回与重排混为一个无法单独度量的步骤。</p>
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
            double finalScore = config.rerankStrategy() == RagEvaluationRerankStrategy.LEXICAL
                    ? clamp(candidate.score()) * 0.55 + lexicalScore * 0.45
                    : -index;
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
