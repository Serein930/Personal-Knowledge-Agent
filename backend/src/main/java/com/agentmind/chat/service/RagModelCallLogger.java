package com.agentmind.chat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 检索增强生成模型调用日志记录器。
 *
 * <p>该组件集中记录回答生成过程中的关键观测信息，避免各个生成器自行拼装日志。
 * 当前只写普通应用日志，后续可以平滑升级为数据库审计表或链路追踪事件。</p>
 */
@Component
public class RagModelCallLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(RagModelCallLogger.class);

    public void logStart(AnswerGenerationRequest request, String answerGenerator, String modelName) {
        LOGGER.info(
                "检索增强生成回答开始：提示词版本={}，回答生成器={}，模型名称={}，引用数量={}，是否拒答={}",
                request.promptVersion(),
                answerGenerator,
                modelName,
                request.citations().size(),
                request.refusalDecision().shouldRefuse()
        );
    }

    public void logSuccess(
            AnswerGenerationRequest request,
            String answerGenerator,
            String modelName,
            long elapsedMillis,
            int answerLength
    ) {
        LOGGER.info(
                "检索增强生成回答完成：提示词版本={}，回答生成器={}，模型名称={}，耗时毫秒={}，回答长度={}",
                request.promptVersion(),
                answerGenerator,
                modelName,
                elapsedMillis,
                answerLength
        );
    }

    public void logFailure(
            AnswerGenerationRequest request,
            String answerGenerator,
            String modelName,
            long elapsedMillis,
            RuntimeException exception
    ) {
        LOGGER.warn(
                "检索增强生成回答失败：提示词版本={}，回答生成器={}，模型名称={}，耗时毫秒={}，失败原因={}",
                request.promptVersion(),
                answerGenerator,
                modelName,
                elapsedMillis,
                exception.getMessage(),
                exception
        );
    }
}
