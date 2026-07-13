package com.agentmind.chat.model.dto;

/**
 * 流式问答异常结束事件。
 *
 * <p>错误码用于前端稳定区分容量不足、生成失败和超时等场景；消息用于展示，
 * 不应包含完整提示词、知识片段或密钥等敏感内容。</p>
 */
public record RagStreamErrorEvent(
        String code,
        String message,
        boolean retryable
) {
}
