package com.agentmind.evaluation.service;

import com.agentmind.chat.memory.model.ChatMessageRole;
import com.agentmind.chat.memory.token.ChatTokenCounter;
import com.agentmind.chat.model.dto.RagCitationResponse;
import com.agentmind.chat.model.dto.TokenUsageResponse;
import com.agentmind.chat.service.AnswerGenerationRequest;
import com.agentmind.chat.service.AnswerGenerator;
import com.agentmind.chat.service.GeneratedAnswer;
import com.agentmind.chat.service.RagPromptTemplate;
import com.agentmind.chat.service.RagRefusalDecision;
import com.agentmind.chat.service.RagRefusalPolicy;
import com.agentmind.evaluation.config.RagEvaluationProperties;
import com.agentmind.evaluation.model.RagEvaluationExperimentConfig;
import com.agentmind.evaluation.model.RagEvaluationPhaseTiming;
import com.agentmind.evaluation.model.RagEvaluationRetrievedSource;
import com.agentmind.knowledge.model.dto.KnowledgeSearchResponse;
import com.agentmind.knowledge.model.dto.KnowledgeSearchResultResponse;
import com.agentmind.knowledge.service.KnowledgeSearchService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/**
 * 单题检索增强生成评估执行器。
 *
 * <p>执行器复用线上检索、拒答、提示词和回答生成端口，但不创建聊天会话，也不污染短期记忆。
 * 因此固定评估可以反复运行，同时保持与线上问答一致的核心决策规则。</p>
 */
@Component
public class RagEvaluationProbe {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);

    private final KnowledgeSearchService searchService;
    private final RagRefusalPolicy refusalPolicy;
    private final RagPromptTemplate promptTemplate;
    private final AnswerGenerator answerGenerator;
    private final ChatTokenCounter tokenCounter;
    private final RagEvaluationProperties evaluationProperties;
    private final RagEvaluationCandidateRanker candidateRanker;

    public RagEvaluationProbe(
            KnowledgeSearchService searchService,
            RagRefusalPolicy refusalPolicy,
            RagPromptTemplate promptTemplate,
            AnswerGenerator answerGenerator,
            ChatTokenCounter tokenCounter,
            RagEvaluationProperties evaluationProperties,
            RagEvaluationCandidateRanker candidateRanker
    ) {
        this.searchService = searchService;
        this.refusalPolicy = refusalPolicy;
        this.promptTemplate = promptTemplate;
        this.answerGenerator = answerGenerator;
        this.tokenCounter = tokenCounter;
        this.evaluationProperties = evaluationProperties;
        this.candidateRanker = candidateRanker;
    }

    public RagEvaluationProbeResult execute(
            Long ownerUserId,
            Long workspaceId,
            String question,
            RagEvaluationExperimentConfig experimentConfig
    ) {
        long startedNanos = System.nanoTime();
        long retrievalStartedNanos = System.nanoTime();
        KnowledgeSearchResponse search = searchService.search(
                workspaceId, question, experimentConfig.candidatePoolSize()
        );
        long retrievalMillis = elapsedMillis(retrievalStartedNanos);
        long rerankStartedNanos = System.nanoTime();
        List<KnowledgeSearchResultResponse> rankedResults = candidateRanker.rank(
                question, search.results(), experimentConfig
        );
        long rerankMillis = elapsedMillis(rerankStartedNanos);
        long generationStartedNanos = System.nanoTime();
        List<RagCitationResponse> citations = toCitations(rankedResults);
        RagRefusalDecision refusalDecision = refusalPolicy.decide(citations);
        String promptContext = promptTemplate.buildPromptContext(question, citations, List.of());
        String generationPrompt = promptTemplate.buildGenerationPrompt(question, promptContext, refusalDecision);
        GeneratedAnswer generated = answerGenerator.generate(new AnswerGenerationRequest(
                workspaceId, ownerUserId, null, null, question, promptTemplate.promptVersion(),
                promptContext, generationPrompt, citations, refusalDecision
        ));
        long generationMillis = elapsedMillis(generationStartedNanos);
        TokenSnapshot tokens = resolveTokens(generated.usage(), generationPrompt, generated.content());
        long totalMillis = elapsedMillis(startedNanos);
        return new RagEvaluationProbeResult(
                IntStream.range(0, rankedResults.size())
                        .mapToObj(index -> toRetrievedSource(rankedResults.get(index), index + 1)).toList(),
                refusalDecision.shouldRefuse(), generated.content(),
                new RagEvaluationPhaseTiming(retrievalMillis, rerankMillis, generationMillis, totalMillis),
                tokens.promptTokens(), tokens.completionTokens(), tokens.estimated(),
                estimateCost(tokens.promptTokens(), tokens.completionTokens())
        );
    }

    private TokenSnapshot resolveTokens(TokenUsageResponse usage, String prompt, String answer) {
        if (usage != null && usage.totalTokens() > 0) {
            return new TokenSnapshot(usage.promptTokens(), usage.completionTokens(), false);
        }
        // 模拟模型没有供应商用量元数据，使用与会话窗口一致的分词端口提供可比较估算值。
        int promptTokens = tokenCounter.countTokens(ChatMessageRole.USER, prompt);
        int completionTokens = tokenCounter.countTokens(ChatMessageRole.ASSISTANT, answer);
        return new TokenSnapshot(promptTokens, completionTokens, true);
    }

    private BigDecimal estimateCost(int promptTokens, int completionTokens) {
        BigDecimal input = evaluationProperties.getInputCostPerMillionTokens()
                .multiply(BigDecimal.valueOf(promptTokens)).divide(ONE_MILLION, 8, RoundingMode.HALF_UP);
        BigDecimal output = evaluationProperties.getOutputCostPerMillionTokens()
                .multiply(BigDecimal.valueOf(completionTokens)).divide(ONE_MILLION, 8, RoundingMode.HALF_UP);
        return input.add(output).setScale(6, RoundingMode.HALF_UP);
    }

    private List<RagCitationResponse> toCitations(List<KnowledgeSearchResultResponse> results) {
        return IntStream.range(0, results.size()).mapToObj(index -> {
            var result = results.get(index);
            return new RagCitationResponse(
                    index + 1, result.documentId(), "文档 #" + result.documentId(),
                    result.chunkId(), result.chunkSequence(), result.headingPath(), result.content(), result.score()
            );
        }).toList();
    }

    private RagEvaluationRetrievedSource toRetrievedSource(
            KnowledgeSearchResultResponse result,
            int rank
    ) {
        return new RagEvaluationRetrievedSource(
                result.chunkId(), result.documentId(), rank, result.score(), result.content()
        );
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }

    private record TokenSnapshot(int promptTokens, int completionTokens, boolean estimated) {
    }
}
