package com.agentmind.chat.service;

import com.agentmind.chat.config.RagAnswerGenerationProperties;
import com.agentmind.chat.model.dto.RagAnswerGenerationMetadataResponse;
import com.agentmind.chat.model.dto.TokenUsageResponse;
import org.springframework.ai.chat.model.ChatModel;
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

    public SpringAiChatModelAnswerGenerator(
            ChatModel chatModel,
            RagAnswerGenerationProperties properties,
            RagModelCallLogger modelCallLogger
    ) {
        this.chatModel = chatModel;
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

        try {
            String answer = chatModel.call(request.generationPrompt());
            long elapsedMillis = elapsedMillis(startNanos);
            modelCallLogger.logSuccess(request, GENERATOR_TYPE, properties.getModelName(), elapsedMillis, answer.length());
            return new GeneratedAnswer(answer, new TokenUsageResponse(0, 0, 0), metadata(request, elapsedMillis));
        } catch (RuntimeException exception) {
            long elapsedMillis = elapsedMillis(startNanos);
            modelCallLogger.logFailure(request, GENERATOR_TYPE, properties.getModelName(), elapsedMillis, exception);
            throw exception;
        }
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
