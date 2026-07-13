package com.agentmind.chat.service;

import java.util.function.Consumer;

/**
 * 可替换的检索增强生成流式回答端口。
 *
 * <p>实现方负责按顺序推送文本增量，并在返回前写入唯一一条最终模型调用审计记录。
 * 传输层通过取消检查端口通知断连或超时，避免生成器直接依赖 SSE 实现。</p>
 */
public interface StreamingAnswerGenerator {

    String generatorType();

    String modelName();

    StreamingGeneratedAnswer generate(
            AnswerGenerationRequest request,
            Consumer<String> deltaConsumer,
            RagStreamCancellationCheck cancellationCheck
    );
}
