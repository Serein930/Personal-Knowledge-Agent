package com.agentmind.chat.model.dto;

/**
 * 流式回答引用来源事件。
 *
 * <p>引用在正文增量之前发送，前端可以提前建立来源列表，并根据正文中的引用编号定位来源。</p>
 */
public record RagStreamCitationEvent(
        int sequence,
        RagCitationResponse citation
) {
}
