package com.agentmind.chat.service;

import com.agentmind.chat.config.RagAnswerGenerationProperties;
import com.agentmind.chat.model.dto.RagAnswerGenerationMetadataResponse;
import com.agentmind.chat.model.dto.TokenUsageResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring AI 流式聊天模型的回答生成适配器。
 *
 * <p>适配器消费模型返回的响应流并立即转交文本增量。若模型在任何内容发出前失败，
 * 可以沿用本地降级策略；若已经发出部分内容，则发送错误终态，避免把降级文本拼接到半截回答后面。</p>
 */
@Component
@ConditionalOnBean(StreamingChatModel.class)
@ConditionalOnProperty(prefix = "agentmind.rag", name = "answer-generator", havingValue = "spring-ai")
public class SpringAiStreamingAnswerGenerator implements StreamingAnswerGenerator {

    private static final String GENERATOR_TYPE = "spring-ai-stream";

    private final StreamingChatModel streamingChatModel;
    private final RagAnswerGenerationProperties properties;
    private final RagModelCallLogger modelCallLogger;

    public SpringAiStreamingAnswerGenerator(
            StreamingChatModel streamingChatModel,
            RagAnswerGenerationProperties properties,
            RagModelCallLogger modelCallLogger
    ) {
        this.streamingChatModel = streamingChatModel;
        this.properties = properties;
        this.modelCallLogger = modelCallLogger;
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
        StringBuilder streamedAnswer = new StringBuilder();
        try {
            if (request.refusalDecision().shouldRefuse()) {
                emitText(request.refusalDecision().reason(), streamedAnswer, deltaConsumer, cancellationCheck);
            } else {
                streamModel(request, streamedAnswer, deltaConsumer, cancellationCheck);
            }
            long elapsedMillis = elapsedMillis(startNanos);
            modelCallLogger.logSuccess(
                    request,
                    GENERATOR_TYPE,
                    properties.getModelName(),
                    elapsedMillis,
                    streamedAnswer.length()
            );
            return result(request, streamedAnswer.length(), elapsedMillis, false, request.refusalDecision().reason());
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
            return handleModelFailure(
                    request,
                    streamedAnswer,
                    deltaConsumer,
                    cancellationCheck,
                    startNanos,
                    exception
            );
        }
    }

    private void streamModel(
            AnswerGenerationRequest request,
            StringBuilder streamedAnswer,
            Consumer<String> deltaConsumer,
            RagStreamCancellationCheck cancellationCheck
    ) {
        Duration timeout = Duration.ofMillis(Math.max(1, properties.getStreamTimeoutMillis()));
        streamingChatModel.stream(request.generationPrompt())
                .timeout(timeout)
                .filter(delta -> delta != null && !delta.isEmpty())
                .doOnNext(delta -> emitDelta(delta, streamedAnswer, deltaConsumer, cancellationCheck))
                .blockLast();
        cancellationCheck.check();
    }

    private StreamingGeneratedAnswer handleModelFailure(
            AnswerGenerationRequest request,
            StringBuilder streamedAnswer,
            Consumer<String> deltaConsumer,
            RagStreamCancellationCheck cancellationCheck,
            long startNanos,
            RuntimeException exception
    ) {
        if (!properties.isSpringAiFailureFallbackEnabled() || !streamedAnswer.isEmpty()) {
            modelCallLogger.logFailure(
                    request,
                    GENERATOR_TYPE,
                    properties.getModelName(),
                    elapsedMillis(startNanos),
                    exception
            );
            throw exception;
        }

        String fallbackAnswer = fallbackAnswer(exception);
        try {
            emitText(fallbackAnswer, streamedAnswer, deltaConsumer, cancellationCheck);
            long elapsedMillis = elapsedMillis(startNanos);
            modelCallLogger.logFallback(
                    request,
                    GENERATOR_TYPE,
                    properties.getModelName(),
                    elapsedMillis,
                    streamedAnswer.length(),
                    exception.getMessage()
            );
            return result(request, streamedAnswer.length(), elapsedMillis, true, fallbackReason(exception));
        } catch (RagStreamTerminatedException terminatedException) {
            modelCallLogger.logCancelled(
                    request,
                    GENERATOR_TYPE,
                    properties.getModelName(),
                    elapsedMillis(startNanos),
                    terminatedException.getMessage()
            );
            throw terminatedException;
        }
    }

    private void emitText(
            String text,
            StringBuilder streamedAnswer,
            Consumer<String> deltaConsumer,
            RagStreamCancellationCheck cancellationCheck
    ) {
        int chunkSize = Math.max(1, properties.getStreamChunkSize());
        for (int start = 0; start < text.length(); start += chunkSize) {
            int end = Math.min(text.length(), start + chunkSize);
            emitDelta(text.substring(start, end), streamedAnswer, deltaConsumer, cancellationCheck);
        }
        cancellationCheck.check();
    }

    private void emitDelta(
            String delta,
            StringBuilder streamedAnswer,
            Consumer<String> deltaConsumer,
            RagStreamCancellationCheck cancellationCheck
    ) {
        cancellationCheck.check();
        deltaConsumer.accept(delta);
        streamedAnswer.append(delta);
    }

    private StreamingGeneratedAnswer result(
            AnswerGenerationRequest request,
            int answerLength,
            long elapsedMillis,
            boolean refused,
            String refusalReason
    ) {
        return new StreamingGeneratedAnswer(
                answerLength,
                new TokenUsageResponse(0, 0, 0),
                new RagAnswerGenerationMetadataResponse(
                        request.promptVersion(),
                        GENERATOR_TYPE,
                        properties.getModelName(),
                        refused || request.refusalDecision().shouldRefuse(),
                        refusalReason,
                        elapsedMillis
                )
        );
    }

    private String fallbackAnswer(RuntimeException exception) {
        return "当前已经完成知识库检索，但真实聊天模型流式调用失败，系统已进入降级模式。"
                + "请稍后重试，或切换回模拟回答生成器继续验证检索链路。失败原因："
                + safeMessage(exception);
    }

    private String fallbackReason(RuntimeException exception) {
        return "真实模型流式调用失败，已按本地降级策略返回可解释结果：" + safeMessage(exception);
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? "未知模型异常" : exception.getMessage();
    }

    private long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
