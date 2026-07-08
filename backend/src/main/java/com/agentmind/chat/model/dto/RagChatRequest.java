package com.agentmind.chat.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * RAG 问答请求 DTO。
 *
 * <p>conversationId 可为空，表示开启新会话。topK 控制检索片段数量，
 * 后续服务层应设置合理默认值并限制最大值，避免单次请求消耗过多模型上下文。</p>
 */
public record RagChatRequest(
        Long conversationId,

        @NotBlank(message = "问题不能为空")
        String question,

        @Min(value = 1, message = "topK 不能小于 1")
        @Max(value = 20, message = "topK 不能大于 20")
        Integer topK,

        RagChatFilterRequest filters
) {
}
