package com.agentmind.chat.service;

import com.agentmind.chat.config.RagAnswerGenerationProperties;
import com.agentmind.chat.model.dto.RagAnswerGenerationMetadataResponse;
import com.agentmind.chat.model.dto.TokenUsageResponse;
import java.time.Duration;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 默认的确定性流式回答生成器。
 *
 * <p>该实现把稳定的模拟回答按固定字符数切分，既不调用付费模型，也不引入随机延迟，
 * 因此适合前端联调和自动化测试。每次生成只会写入成功、失败或取消中的一个最终审计状态。</p>
 */
@Component
@ConditionalOnProperty(prefix = "agentmind.rag", name = "answer-generator", havingValue = "mock", matchIfMissing = true)
public class MockStreamingAnswerGenerator implements StreamingAnswerGenerator {

    private static final String GENERATOR_TYPE = "mock-stream";

    private final RagAnswerGenerationProperties properties;
    private final RagModelCallLogger modelCallLogger;
    private final MockAnswerComposer answerComposer;

    public MockStreamingAnswerGenerator(
            RagAnswerGenerationProperties properties,
            RagModelCallLogger modelCallLogger,
            MockAnswerComposer answerComposer
    ) {
        this.properties = properties;
        this.modelCallLogger = modelCallLogger;
        this.answerComposer = answerComposer;
    }

    @Override
    public String generatorType() {
        return GENERATOR_TYPE;
    }

    @Override
    public String modelName() {
        return properties.getModelName();
    }

    @Override
    public StreamingGeneratedAnswer generate(
            AnswerGenerationRequest request,
            Consumer<String> deltaConsumer,
            RagStreamCancellationCheck cancellationCheck
    ) {
        long startNanos = System.nanoTime();
        modelCallLogger.logStart(request, GENERATOR_TYPE, properties.getModelName());
        try {
            String answer = answerComposer.compose(request);
            emitChunks(answer, deltaConsumer, cancellationCheck);
            long elapsedMillis = elapsedMillis(startNanos);
            modelCallLogger.logSuccess(request, GENERATOR_TYPE, properties.getModelName(), elapsedMillis, answer.length());
            return new StreamingGeneratedAnswer(
                    answer.length(),
                    new TokenUsageResponse(0, 0, 0),
                    metadata(request, elapsedMillis)
            );
        } catch (RagStreamTerminatedException exception) {
            modelCallLogger.logCancelled(
                    request,
                    GENERATOR_TYPE,
                    properties.getModelName(),
                    elapsedMillis(startNanos),
                    exception.getMessage()
            );
            throw exception;
        } catch (RuntimeException exception) {
            modelCallLogger.logFailure(
                    request,
                    GENERATOR_TYPE,
                    properties.getModelName(),
                    elapsedMillis(startNanos),
                    exception
            );
            throw exception;
        }
    }

    private void emitChunks(
            String answer,
            Consumer<String> deltaConsumer,
            RagStreamCancellationCheck cancellationCheck
    ) {
        int chunkSize = Math.max(1, properties.getStreamChunkSize());
        for (int start = 0; start < answer.length(); start += chunkSize) {
            cancellationCheck.check();
            int end = Math.min(answer.length(), start + chunkSize);
            deltaConsumer.accept(answer.substring(start, end));
        }
        cancellationCheck.check();
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
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
