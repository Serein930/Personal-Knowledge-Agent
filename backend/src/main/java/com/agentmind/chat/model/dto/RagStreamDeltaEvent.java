package com.agentmind.chat.model.dto;

/**
 * 回答文本增量事件。
 *
 * <p>序号从 1 开始递增，前端应按序追加文本，并可以用序号识别重复事件。</p>
 */
public record RagStreamDeltaEvent(
        int sequence,
        String content
) {
}
