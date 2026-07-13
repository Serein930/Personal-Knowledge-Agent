package com.agentmind.chat.service;

import com.agentmind.chat.model.RagModelCallObservation;
import com.agentmind.chat.model.RagModelCallStatus;
import com.agentmind.chat.repository.RagModelCallObservationRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 检索增强生成模型调用日志记录器。
 *
 * <p>该组件集中构造回答生成过程中的观测记录，并同时写入应用日志和当前启用的观测仓库，
 * 避免各个生成器分别维护日志字段和审计字段。</p>
 */
@Component
public class RagModelCallLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(RagModelCallLogger.class);

    private final RagModelCallObservationRepository repository;

    public RagModelCallLogger(RagModelCallObservationRepository repository) {
        this.repository = repository;
    }

    public void logStart(AnswerGenerationRequest request, String answerGenerator, String modelName) {
        RagModelCallObservation observation = observation(
                request,
                answerGenerator,
                modelName,
                RagModelCallStatus.STARTED,
                request.refusalDecision().shouldRefuse(),
                0,
                0,
                ""
        );
        LOGGER.info(
                "检索增强生成回答开始：提示词版本={}，回答生成器={}，模型名称={}，引用数量={}，是否拒答={}",
                observation.promptVersion(),
                observation.answerGenerator(),
                observation.modelName(),
                observation.citationCount(),
                observation.refused()
        );
    }

    public void logSuccess(
            AnswerGenerationRequest request,
            String answerGenerator,
            String modelName,
            long elapsedMillis,
            int answerLength
    ) {
        RagModelCallObservation observation = observation(
                request,
                answerGenerator,
                modelName,
                RagModelCallStatus.SUCCEEDED,
                request.refusalDecision().shouldRefuse(),
                elapsedMillis,
                answerLength,
                ""
        );
        LOGGER.info(
                "检索增强生成回答完成：提示词版本={}，回答生成器={}，模型名称={}，耗时毫秒={}，回答长度={}",
                observation.promptVersion(),
                observation.answerGenerator(),
                observation.modelName(),
                observation.elapsedMillis(),
                observation.answerLength()
        );
        repository.save(observation);
    }

    public void logFailure(
            AnswerGenerationRequest request,
            String answerGenerator,
            String modelName,
            long elapsedMillis,
            RuntimeException exception
    ) {
        RagModelCallObservation observation = observation(
                request,
                answerGenerator,
                modelName,
                RagModelCallStatus.FAILED,
                request.refusalDecision().shouldRefuse(),
                elapsedMillis,
                0,
                exception.getMessage()
        );
        LOGGER.warn(
                "检索增强生成回答失败：提示词版本={}，回答生成器={}，模型名称={}，耗时毫秒={}，失败原因={}",
                observation.promptVersion(),
                observation.answerGenerator(),
                observation.modelName(),
                observation.elapsedMillis(),
                observation.failureReason(),
                exception
        );
        repository.save(observation);
    }

    public void logFallback(
            AnswerGenerationRequest request,
            String answerGenerator,
            String modelName,
            long elapsedMillis,
            int answerLength,
            String failureReason
    ) {
        String normalizedFailureReason = failureReason == null ? "" : failureReason;
        RagModelCallObservation observation = observation(
                request,
                answerGenerator,
                modelName,
                RagModelCallStatus.FALLBACK,
                true,
                elapsedMillis,
                answerLength,
                normalizedFailureReason
        );
        LOGGER.warn(
                "检索增强生成回答已降级：提示词版本={}，回答生成器={}，模型名称={}，耗时毫秒={}，降级原因={}",
                observation.promptVersion(),
                observation.answerGenerator(),
                observation.modelName(),
                observation.elapsedMillis(),
                observation.failureReason()
        );
        repository.save(observation);
    }

    /**
     * 记录客户端断开或流式会话超时产生的取消终态。
     *
     * <p>取消记录只由流式生成器在终止信号首次冒泡时写入，SSE 编排层不会重复保存审计记录。</p>
     */
    public void logCancelled(
            AnswerGenerationRequest request,
            String answerGenerator,
            String modelName,
            long elapsedMillis,
            String reason
    ) {
        RagModelCallObservation observation = observation(
                request,
                answerGenerator,
                modelName,
                RagModelCallStatus.CANCELLED,
                request.refusalDecision().shouldRefuse(),
                elapsedMillis,
                0,
                reason
        );
        LOGGER.info(
                "检索增强生成流式回答已取消：提示词版本={}，回答生成器={}，模型名称={}，耗时毫秒={}，取消原因={}",
                observation.promptVersion(),
                observation.answerGenerator(),
                observation.modelName(),
                observation.elapsedMillis(),
                observation.failureReason()
        );
        repository.save(observation);
    }

    private RagModelCallObservation observation(
            AnswerGenerationRequest request,
            String answerGenerator,
            String modelName,
            RagModelCallStatus status,
            boolean refused,
            long elapsedMillis,
            int answerLength,
            String failureReason
    ) {
        return new RagModelCallObservation(
                UUID.randomUUID().toString(),
                request.workspaceId(),
                request.promptVersion(),
                answerGenerator,
                modelName,
                request.citations().size(),
                refused,
                status,
                elapsedMillis,
                answerLength,
                failureReason == null ? "" : failureReason,
                OffsetDateTime.now()
        );
    }
}
