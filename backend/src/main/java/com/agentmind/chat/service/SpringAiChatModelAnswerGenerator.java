package com.agentmind.chat.service;

import com.agentmind.agent.audit.model.dto.AgentToolCallSummaryResponse;
import com.agentmind.agent.service.AgentToolCallingOrchestrator;
import com.agentmind.agent.tool.model.AgentToolExecutionContext;
import com.agentmind.agent.tool.springai.SpringAiAgentToolCallbackAdapter;
import com.agentmind.chat.config.RagAnswerGenerationProperties;
import com.agentmind.chat.model.dto.RagAnswerGenerationMetadataResponse;
import com.agentmind.chat.model.dto.TokenUsageResponse;
import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 真实聊天模型回答生成适配器骨架。
 *
 * <p>该实现只在显式切换到真实模型模式，并且容器中已经存在聊天模型客户端时启用。
 * 业务服务仍然只依赖回答生成端口，因此默认模拟实现和真实模型实现可以通过配置平滑切换。</p>
 */
@Component
@ConditionalOnBean(ChatModel.class)
@ConditionalOnProperty(prefix = "agentmind.rag", name = "answer-generator", havingValue = "spring-ai")
public class SpringAiChatModelAnswerGenerator implements AnswerGenerator {

    private static final String GENERATOR_TYPE = "spring-ai";

    private final ChatModel chatModel;
    private final RagAnswerGenerationProperties properties;
    private final RagModelCallLogger modelCallLogger;
    private final SpringAiAgentToolCallbackAdapter toolCallbackAdapter;
    private final AgentToolCallingOrchestrator toolCallingOrchestrator;
    private final ToolCallingManager toolCallingManager;

    @Autowired
    public SpringAiChatModelAnswerGenerator(
            ChatModel chatModel,
            RagAnswerGenerationProperties properties,
            RagModelCallLogger modelCallLogger,
            SpringAiAgentToolCallbackAdapter toolCallbackAdapter,
            AgentToolCallingOrchestrator toolCallingOrchestrator,
            ToolCallingManager toolCallingManager
    ) {
        this.chatModel = chatModel;
        this.properties = properties;
        this.modelCallLogger = modelCallLogger;
        this.toolCallbackAdapter = toolCallbackAdapter;
        this.toolCallingOrchestrator = toolCallingOrchestrator;
        this.toolCallingManager = toolCallingManager;
    }

    public SpringAiChatModelAnswerGenerator(
            ChatModel chatModel,
            RagAnswerGenerationProperties properties,
            RagModelCallLogger modelCallLogger,
            SpringAiAgentToolCallbackAdapter toolCallbackAdapter,
            AgentToolCallingOrchestrator toolCallingOrchestrator
    ) {
        this(
                chatModel,
                properties,
                modelCallLogger,
                toolCallbackAdapter,
                toolCallingOrchestrator,
                ToolCallingManager.builder().build()
        );
    }

    /**
     * 兼容原有不关注工具调用的生成器单元测试。
     */
    public SpringAiChatModelAnswerGenerator(
            ChatModel chatModel,
            RagAnswerGenerationProperties properties,
            RagModelCallLogger modelCallLogger
    ) {
        this.chatModel = chatModel;
        this.properties = properties;
        this.modelCallLogger = modelCallLogger;
        this.toolCallbackAdapter = null;
        this.toolCallingOrchestrator = null;
        this.toolCallingManager = null;
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

        try {
            ModelAnswer modelAnswer = callModel(request);
            String answer = modelAnswer.content();
            long elapsedMillis = elapsedMillis(startNanos);
            modelCallLogger.logSuccess(request, GENERATOR_TYPE, properties.getModelName(), elapsedMillis, answer.length());
            return new GeneratedAnswer(
                    answer,
                    modelAnswer.usage(),
                    metadata(request, elapsedMillis),
                    modelAnswer.toolCalls()
            );
        } catch (RuntimeException exception) {
            long elapsedMillis = elapsedMillis(startNanos);
            if (!properties.isSpringAiFailureFallbackEnabled()) {
                modelCallLogger.logFailure(request, GENERATOR_TYPE, properties.getModelName(), elapsedMillis, exception);
                throw exception;
            }
            String fallbackAnswer = fallbackAnswer(exception);
            modelCallLogger.logFallback(
                    request,
                    GENERATOR_TYPE,
                    properties.getModelName(),
                    elapsedMillis,
                    fallbackAnswer.length(),
                    exception.getMessage()
            );
            return new GeneratedAnswer(
                    fallbackAnswer,
                    new TokenUsageResponse(0, 0, 0),
                    fallbackMetadata(request, elapsedMillis, exception),
                    findToolCalls(request)
            );
        }
    }

    /**
     * 使用 ChatClient 交给 Spring AI 执行模型工具循环。
     *
     * <p>工具回调只暴露只读工具，执行结果会作为 ToolResponse 自动交还模型，直到模型生成最终回答。</p>
     */
    private ModelAnswer callModel(AnswerGenerationRequest request) {
        if (!properties.isToolCallingEnabled() || toolCallbackAdapter == null || toolCallingOrchestrator == null) {
            return new ModelAnswer(
                    chatModel.call(request.generationPrompt()),
                    new TokenUsageResponse(0, 0, 0),
                    List.of()
            );
        }

        AgentToolExecutionContext executionContext = executionContext(request);
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(toolCallbackAdapter.createReadOnlyCallbacks(executionContext))
                .toolContext(toolCallbackAdapter.createToolContext(executionContext))
                .internalToolExecutionEnabled(false)
                .build();
        Prompt prompt = new Prompt(request.generationPrompt(), options);
        int maxRoundTrips = Math.max(1, properties.getMaxToolRoundTrips());
        for (int round = 0; round <= maxRoundTrips; round++) {
            ChatResponse response = chatModel.call(prompt);
            validateResponse(response);
            if (!response.hasToolCalls()) {
                return new ModelAnswer(
                        response.getResult().getOutput().getText(),
                        toUsage(response),
                        findToolCalls(request)
                );
            }
            if (round == maxRoundTrips) {
                throw new IllegalStateException("模型工具调用超过最大往返次数：" + maxRoundTrips);
            }
            ToolExecutionResult executionResult = toolCallingManager.executeToolCalls(prompt, response);
            prompt = new Prompt(executionResult.conversationHistory(), options);
        }
        throw new IllegalStateException("模型工具调用循环异常结束");
    }

    private void validateResponse(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("真实模型没有返回有效回答");
        }
    }

    private TokenUsageResponse toUsage(ChatResponse response) {
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        if (usage == null) {
            return new TokenUsageResponse(0, 0, 0);
        }
        int promptTokens = usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
        int completionTokens = usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
        Integer totalTokens = usage.getTotalTokens();
        return new TokenUsageResponse(
                promptTokens,
                completionTokens,
                totalTokens == null ? promptTokens + completionTokens : totalTokens
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

    private RagAnswerGenerationMetadataResponse fallbackMetadata(
            AnswerGenerationRequest request,
            long elapsedMillis,
            RuntimeException exception
    ) {
        return new RagAnswerGenerationMetadataResponse(
                request.promptVersion(),
                GENERATOR_TYPE,
                properties.getModelName(),
                true,
                "真实模型调用失败，已按本地降级策略返回可解释结果：" + exception.getMessage(),
                elapsedMillis
        );
    }

    private String fallbackAnswer(RuntimeException exception) {
        return "当前已经完成知识库检索，但真实聊天模型调用失败，系统已进入降级模式。"
                + "请稍后重试，或切换回模拟回答生成器继续验证检索链路。失败原因："
                + exception.getMessage();
    }

    private long elapsedMillis(long startNanos) {
        return java.time.Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    private record ModelAnswer(
            String content,
            TokenUsageResponse usage,
            List<AgentToolCallSummaryResponse> toolCalls
    ) {
    }
}
