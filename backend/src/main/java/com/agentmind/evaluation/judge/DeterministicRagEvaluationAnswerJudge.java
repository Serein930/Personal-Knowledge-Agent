package com.agentmind.evaluation.judge;

import com.agentmind.evaluation.config.RagEvaluationProperties;
import com.agentmind.evaluation.metric.RagEvaluationTextSimilarity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 不依赖外部模型的确定性裁判。
 *
 * <p>该实现继续使用词项相似度，适合 CI、回归测试和模型不可用时的可解释降级；结果证据会明确
 * 标记其并非真实大模型判断，避免将近似指标误认为人工质量结论。</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "agentmind.evaluation.judge",
        name = "type",
        havingValue = "deterministic",
        matchIfMissing = true
)
public class DeterministicRagEvaluationAnswerJudge implements RagEvaluationAnswerJudge {

    private final RagEvaluationTextSimilarity textSimilarity;
    private final RagEvaluationProperties properties;

    public DeterministicRagEvaluationAnswerJudge(
            RagEvaluationTextSimilarity textSimilarity,
            RagEvaluationProperties properties
    ) {
        this.textSimilarity = textSimilarity;
        this.properties = properties;
    }

    @Override
    public RagEvaluationJudgeResult judge(RagEvaluationJudgeRequest request) {
        return deterministicResult(request, false, "使用固定词项相似度计算可重复近似分");
    }

    RagEvaluationJudgeResult deterministicResult(
            RagEvaluationJudgeRequest request,
            boolean fallbackUsed,
            String rationale
    ) {
        double faithfulness = round(textSimilarity.similarity(request.answer(), request.sourceContext()) * 100.0);
        double answerRelevance = round(textSimilarity.similarity(request.question(), request.answer()) * 100.0);
        return new RagEvaluationJudgeResult(
                faithfulness,
                answerRelevance,
                new RagEvaluationJudgeEvidence(
                        "deterministic",
                        properties.getJudgeModelName(),
                        properties.getJudgePromptVersion(),
                        rationale,
                        fallbackUsed
                )
        );
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
