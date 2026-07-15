package com.agentmind.evaluation.metric;

import com.agentmind.evaluation.model.RagEvaluationCase;
import com.agentmind.evaluation.model.RagEvaluationCaseResult;
import com.agentmind.evaluation.model.RagEvaluationMetrics;
import com.agentmind.evaluation.model.RagEvaluationRetrievedSource;
import com.agentmind.evaluation.service.RagEvaluationProbeResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 固定评估集指标计算器。
 *
 * <p>Recall@K 和 MRR 只统计声明了相关来源的可回答题；拒答准确率统计全部题；
 * 引用覆盖率统计应回答题是否至少命中一个相关来源。各指标分母分离，避免互相污染。</p>
 */
@Component
public class RagEvaluationMetricCalculator {

    public RagEvaluationCaseResult calculateCase(RagEvaluationCase evaluationCase, RagEvaluationProbeResult probe) {
        boolean chunkLevel = !evaluationCase.expectedRelevantChunkIds().isEmpty();
        int expectedCount = chunkLevel
                ? new HashSet<>(evaluationCase.expectedRelevantChunkIds()).size()
                : new HashSet<>(evaluationCase.expectedRelevantDocumentIds()).size();
        Set<String> matchedChunks = new HashSet<>();
        Set<Long> matchedDocuments = new HashSet<>();
        Integer firstRelevantRank = null;
        for (RagEvaluationRetrievedSource source : probe.retrievedSources()) {
            boolean relevant = chunkLevel
                    ? evaluationCase.expectedRelevantChunkIds().contains(source.chunkId())
                    : evaluationCase.expectedRelevantDocumentIds().contains(source.documentId());
            if (!relevant) {
                continue;
            }
            if (chunkLevel) {
                matchedChunks.add(source.chunkId());
            } else {
                matchedDocuments.add(source.documentId());
            }
            if (firstRelevantRank == null) {
                firstRelevantRank = source.rank();
            }
        }
        int matchedCount = chunkLevel ? matchedChunks.size() : matchedDocuments.size();
        double recall = expectedCount == 0 ? 0 : matchedCount * 100.0 / expectedCount;
        double reciprocalRank = firstRelevantRank == null ? 0 : 1.0 / firstRelevantRank;
        boolean citationCovered = !evaluationCase.expectedRefusal() && matchedCount > 0 && !probe.refused();
        double keywordCoverage = keywordCoverage(evaluationCase.expectedAnswerKeywords(), probe.answer());
        return new RagEvaluationCaseResult(
                evaluationCase.caseKey(), evaluationCase.question(), probe.retrievedSources(), matchedCount,
                expectedCount, firstRelevantRank, round(recall), round(reciprocalRank), citationCovered,
                evaluationCase.expectedRefusal(), probe.refused(),
                evaluationCase.expectedRefusal() == probe.refused(), keywordCoverage,
                probe.elapsedMillis(), probe.promptTokens(), probe.completionTokens(),
                probe.tokenUsageEstimated(), probe.estimatedCostUsd()
        );
    }

    public RagEvaluationMetrics aggregate(List<RagEvaluationCaseResult> results) {
        List<RagEvaluationCaseResult> retrievalCases = results.stream()
                .filter(result -> result.relevantExpectedCount() > 0).toList();
        List<RagEvaluationCaseResult> answerableCases = results.stream()
                .filter(result -> !result.expectedRefusal()).toList();
        List<RagEvaluationCaseResult> keywordCases = results.stream()
                .filter(result -> result.answerKeywordCoverage() >= 0).toList();
        int promptTokens = results.stream().mapToInt(RagEvaluationCaseResult::promptTokens).sum();
        int completionTokens = results.stream().mapToInt(RagEvaluationCaseResult::completionTokens).sum();
        BigDecimal cost = results.stream().map(RagEvaluationCaseResult::estimatedCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(6, RoundingMode.HALF_UP);
        return new RagEvaluationMetrics(
                results.size(), average(retrievalCases, RagEvaluationCaseResult::recallAtK),
                average(retrievalCases, RagEvaluationCaseResult::reciprocalRank),
                percent(answerableCases.stream().filter(RagEvaluationCaseResult::citationCovered).count(),
                        answerableCases.size()),
                percent(results.stream().filter(RagEvaluationCaseResult::refusalCorrect).count(), results.size()),
                average(keywordCases, RagEvaluationCaseResult::answerKeywordCoverage),
                Math.round(results.stream().mapToLong(RagEvaluationCaseResult::elapsedMillis).average().orElse(0)),
                promptTokens, completionTokens, promptTokens + completionTokens,
                results.stream().anyMatch(RagEvaluationCaseResult::tokenUsageEstimated), cost
        );
    }

    private double keywordCoverage(List<String> keywords, String answer) {
        if (keywords.isEmpty()) {
            // -1 表示该题未配置答案关键词，不进入聚合分母。
            return -1;
        }
        String normalizedAnswer = answer == null ? "" : answer.toLowerCase(Locale.ROOT);
        Set<String> normalizedKeywords = keywords.stream()
                .map(value -> value.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        long matched = normalizedKeywords.stream()
                .filter(normalizedAnswer::contains).distinct().count();
        return round(matched * 100.0 / normalizedKeywords.size());
    }

    private double average(List<RagEvaluationCaseResult> values, ValueExtractor extractor) {
        return round(values.stream().mapToDouble(extractor::value).average().orElse(0));
    }

    private double percent(long numerator, long denominator) {
        return denominator == 0 ? 0 : round(numerator * 100.0 / denominator);
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    @FunctionalInterface
    private interface ValueExtractor {
        double value(RagEvaluationCaseResult result);
    }
}
