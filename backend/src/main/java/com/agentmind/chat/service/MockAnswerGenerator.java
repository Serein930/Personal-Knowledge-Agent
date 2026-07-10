package com.agentmind.chat.service;

import com.agentmind.chat.config.RagAnswerGenerationProperties;
import com.agentmind.chat.model.dto.RagCitationResponse;
import com.agentmind.chat.model.dto.RagAnswerGenerationMetadataResponse;
import com.agentmind.chat.model.dto.TokenUsageResponse;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 默认的确定性回答生成器。
 *
 * <p>该实现不会伪装成真实大模型，只根据检索片段拼出稳定回答，用于本地开发、前端联调和自动化测试。
 * 当配置切换到真实模型适配器时，业务层仍然只面对回答生成端口。</p>
 */
@Component
@ConditionalOnProperty(prefix = "agentmind.rag", name = "answer-generator", havingValue = "mock", matchIfMissing = true)
public class MockAnswerGenerator implements AnswerGenerator {

    private static final String GENERATOR_TYPE = "mock";

    private final RagAnswerGenerationProperties properties;
    private final RagModelCallLogger modelCallLogger;

    public MockAnswerGenerator(RagAnswerGenerationProperties properties, RagModelCallLogger modelCallLogger) {
        this.properties = properties;
        this.modelCallLogger = modelCallLogger;
    }

    @Override
    public GeneratedAnswer generate(AnswerGenerationRequest request) {
        long startNanos = System.nanoTime();
        modelCallLogger.logStart(request, GENERATOR_TYPE, properties.getModelName());
        if (request.refusalDecision().shouldRefuse()) {
            long elapsedMillis = elapsedMillis(startNanos);
            modelCallLogger.logSuccess(
                    request,
                    GENERATOR_TYPE,
                    properties.getModelName(),
                    elapsedMillis,
                    request.refusalDecision().reason().length()
            );
            return new GeneratedAnswer(
                    request.refusalDecision().reason(),
                    new TokenUsageResponse(0, 0, 0),
                    metadata(request, elapsedMillis)
            );
        }

        String citedSummary = request.citations().stream()
                .limit(3)
                .map(this::toCitedSentence)
                .collect(Collectors.joining(" "));
        String answer = "根据当前知识库检索结果，可以得到以下回答：" + citedSummary
                + "。以上内容来自模拟生成器，仅用于验证检索增强生成链路；后续可以由真实模型适配器替换。";
        long elapsedMillis = elapsedMillis(startNanos);
        modelCallLogger.logSuccess(request, GENERATOR_TYPE, properties.getModelName(), elapsedMillis, answer.length());
        return new GeneratedAnswer(answer, new TokenUsageResponse(0, 0, 0), metadata(request, elapsedMillis));
    }

    private String toCitedSentence(RagCitationResponse citation) {
        String excerpt = citation.excerpt();
        String trimmed = excerpt.length() > 180 ? excerpt.substring(0, 180) + "..." : excerpt;
        return trimmed + " [" + citation.index() + "]";
    }

    private RagAnswerGenerationMetadataResponse metadata(AnswerGenerationRequest request, long elapsedMillis) {
        return new RagAnswerGenerationMetadataResponse(
                request.promptVersion(),
                GENERATOR_TYPE,
                properties.getModelName(),
                request.refusalDecision().shouldRefuse(),
                request.refusalDecision().reason(),
                elapsedMillis
        );
    }

    private long elapsedMillis(long startNanos) {
        return java.time.Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
