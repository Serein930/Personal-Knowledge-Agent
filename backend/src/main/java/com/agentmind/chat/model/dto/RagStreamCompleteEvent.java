package com.agentmind.chat.model.dto;

/**
 * 流式问答正常完成事件。
 *
 * <p>该事件只在全部增量发送成功并且模型调用审计完成后发送。收到该事件后，
 * 前端可以把临时消息标记为已完成。</p>
 */
public record RagStreamCompleteEvent(
        Long conversationId,
        Long messageId,
        int deltaCount,
        int answerLength,
        RagAnswerGenerationMetadataResponse generationMetadata,
        TokenUsageResponse usage
) {
}
