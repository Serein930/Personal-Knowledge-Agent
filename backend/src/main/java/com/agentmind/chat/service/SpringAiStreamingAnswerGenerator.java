package com.agentmind.chat.service;

import com.agentmind.agent.audit.model.dto.AgentToolCallSummaryResponse;
import com.agentmind.agent.service.AgentToolCallingOrchestrator;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.agent.tool.springai.SpringAiAgentToolCallbackAdapter;
import com.agentmind.chat.config.RagAnswerGenerationProperties;
import com.agentmind.chat.model.dto.RagAnswerGenerationMetadataResponse;
import com.agentmind.chat.model.dto.TokenUsageResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.beans.factory.annotation.Autowired;
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
@ConditionalOnBean(ChatModel.class)
@ConditionalOnProperty(prefix = "agentmind.rag", name = "answer-generator", havingValue = "spring-ai")
public class SpringAiStreamingAnswerGenerator implements StreamingAnswerGenerator {

    private static final String GENERATOR_TYPE = "spring-ai-stream";

    private final StreamingChatModel streamingChatModel;
    private final ChatModel planningChatModel;
    private final RagAnswerGenerationProperties properties;
    private final RagModelCallLogger modelCallLogger;
    private final SpringAiAgentToolCallbackAdapter toolCallbackAdapter;
    private final AgentToolCallingOrchestrator toolCallingOrchestrator;
    private final ToolCallingManager toolCallingManager;

    @Autowired
    public SpringAiStreamingAnswerGenerator(
            ChatModel chatModel,
            RagAnswerGenerationProperties properties,
            RagModelCallLogger modelCallLogger,
            SpringAiAgentToolCallbackAdapter toolCallbackAdapter,
            AgentToolCallingOrchestrator toolCallingOrchestrator,
            ToolCallingManager toolCallingManager
    ) {
        this.streamingChatModel = chatModel;
        this.planningChatModel = chatModel;
        this.properties = properties;
        this.modelCallLogger = modelCallLogger;
        this.toolCallbackAdapter = toolCallbackAdapter;
        this.toolCallingOrchestrator = toolCallingOrchestrator;
        this.toolCallingManager = toolCallingManager;
    }

    /**
     * 兼容原有只验证流式失败与降级行为的单元测试。
     */
    public SpringAiStreamingAnswerGenerator(
            StreamingChatModel streamingChatModel,
            RagAnswerGenerationProperties properties,
            RagModelCallLogger modelCallLogger
    ) {
        this.streamingChatModel = streamingChatModel;
        this.planningChatModel = null;
        this.properties = properties;
        this.modelCallLogger = modelCallLogger;
        this.toolCallbackAdapter = null;
        this.toolCallingOrchestrator = null;
        this.toolCallingManager = null;
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
        List<AgentToolCallSummaryResponse> toolCalls = List.of();
        TokenUsageResponse tokenUsage = ChatModelTokenUsage.zero();
        try {
            if (request.refusalDecision().shouldRefuse()) {
                emitText(request.refusalDecision().reason(), streamedAnswer, deltaConsumer, cancellationCheck);
            } else {
                StreamingModelAnswer modelAnswer = streamModel(
                        request, streamedAnswer, deltaConsumer, cancellationCheck);
                toolCalls = modelAnswer.toolCalls();
                tokenUsage = modelAnswer.usage();
            }
            long elapsedMillis = elapsedMillis(startNanos);
            modelCallLogger.logSuccess(
                    request,
                    GENERATOR_TYPE,
                    properties.getModelName(),
                    elapsedMillis,
                    streamedAnswer.length()
            );
            return result(
                    request,
                    streamedAnswer.length(),
                    elapsedMillis,
                    false,
                    request.refusalDecision().reason(),
                    tokenUsage,
                    toolCalls
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

    private StreamingModelAnswer streamModel(
            AnswerGenerationRequest request,
            StringBuilder streamedAnswer,
            Consumer<String> deltaConsumer,
            RagStreamCancellationCheck cancellationCheck
    ) {
        Duration timeout = Duration.ofMillis(Math.max(1, properties.getStreamTimeoutMillis()));
        if (properties.isToolCallingEnabled()
                && planningChatModel != null
                && toolCallbackAdapter != null
                && toolCallingOrchestrator != null
                && toolCallingManager != null) {
            AgentToolExecutionContext executionContext = executionContext(request);
            ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                    .toolCallbacks(toolCallbackAdapter.createReadOnlyCallbacks(executionContext))
                    .toolContext(toolCallbackAdapter.createToolContext(executionContext))
                    .internalToolExecutionEnabled(false)
                    .build();
            Prompt planningPrompt = new Prompt(request.generationPrompt(), options);
            ChatResponse planningResponse = planningChatModel.call(planningPrompt);
            validatePlanningResponse(planningResponse);
            if (planningResponse.hasToolCalls()) {
                ToolExecutionResult executionResult = toolCallingManager.executeToolCalls(
                        planningPrompt,
                        planningResponse
                );
                // 工具执行完成后关闭工具定义，再流式生成最终回答，避免流中再次产生无法安全处理的工具请求。
                TokenUsageResponse streamUsage = streamPrompt(
                        new Prompt(executionResult.conversationHistory()),
                        timeout,
                        streamedAnswer,
                        deltaConsumer,
                        cancellationCheck
                );
                cancellationCheck.check();
                return new StreamingModelAnswer(
                        findToolCalls(request),
                        ChatModelTokenUsage.add(ChatModelTokenUsage.from(planningResponse), streamUsage)
                );
            } else {
                emitText(
                        planningResponse.getResult().getOutput().getText(),
                        streamedAnswer,
                        deltaConsumer,
                        cancellationCheck
                );
            }
            cancellationCheck.check();
            return new StreamingModelAnswer(findToolCalls(request), ChatModelTokenUsage.from(planningResponse));
        }

        TokenUsageResponse streamUsage = streamPrompt(
                new Prompt(request.generationPrompt()),
                timeout,
                streamedAnswer,
                deltaConsumer,
                cancellationCheck
        );
        cancellationCheck.check();
        return new StreamingModelAnswer(List.of(), streamUsage);
    }

    private TokenUsageResponse streamPrompt(
            Prompt prompt,
            Duration timeout,
            StringBuilder streamedAnswer,
            Consumer<String> deltaConsumer,
            RagStreamCancellationCheck cancellationCheck
    ) {
        java.util.concurrent.atomic.AtomicReference<TokenUsageResponse> latestUsage =
                new java.util.concurrent.atomic.AtomicReference<>(ChatModelTokenUsage.zero());
        streamingChatModel.stream(prompt)
                .timeout(timeout)
                .doOnNext(response -> {
                    TokenUsageResponse usage = ChatModelTokenUsage.from(response);
                    if (usage.totalTokens() > 0) {
                        latestUsage.set(usage);
                    }
                })
                .map(response -> response == null
                        || response.getResult() == null
                        || response.getResult().getOutput() == null
                        ? ""
                        : response.getResult().getOutput().getText())
                .filter(delta -> delta != null && !delta.isEmpty())
                .doOnNext(delta -> emitDelta(delta, streamedAnswer, deltaConsumer, cancellationCheck))
                .blockLast();
        return latestUsage.get();
    }

    private void validatePlanningResponse(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("真实模型没有返回有效的工具决策");
        }
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

        String fallbackAnswer = fallbackAnswer();
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
            return result(
                    request,
                    streamedAnswer.length(),
                    elapsedMillis,
                    true,
                    fallbackReason(),
                    ChatModelTokenUsage.zero(),
                    findToolCalls(request)
            );
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
            String refusalReason,
            TokenUsageResponse tokenUsage,
            List<AgentToolCallSummaryResponse> toolCalls
    ) {
        return new StreamingGeneratedAnswer(
                answerLength,
                tokenUsage,
                new RagAnswerGenerationMetadataResponse(
                        request.promptVersion(),
                        GENERATOR_TYPE,
                        properties.getModelName(),
                        refused || request.refusalDecision().shouldRefuse(),
                        refusalReason,
                        elapsedMillis
                ),
                toolCalls
        );
    }

    private List<AgentToolCallSummaryResponse> findToolCalls(AnswerGenerationRequest request) {
        if (toolCallingOrchestrator == null || request.conversationId() == null || request.messageId() == null) {
            return List.of();
        }
        return toolCallingOrchestrator.findAuditsForExecution(executionContext(request));
    }

    private AgentToolExecutionContext executionContext(AnswerGenerationRequest request) {
        return new AgentToolExecutionContext(
                request.ownerUserId(),
                request.workspaceId(),
                request.conversationId(),
                request.messageId()
        );
    }

    private String fallbackAnswer() {
        return "当前已经完成知识库检索，但真实聊天模型流式调用失败，系统已进入降级模式。"
                + "请稍后重试，或切换回模拟回答生成器继续验证检索链路。";
    }

    private String fallbackReason() {
        return "真实模型流式调用失败，系统已返回安全降级结果";
    }

    private long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    private record StreamingModelAnswer(
            List<AgentToolCallSummaryResponse> toolCalls,
            TokenUsageResponse usage
    ) {
    }
}
