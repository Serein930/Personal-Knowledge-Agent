package com.agentmind.chat.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * RAG 问答请求。
 *
 * <p>当前阶段已经支持检索上下文组装和模拟回答生成。后续接入认证、记忆和真实模型后，
 * 仍然可以沿用该请求契约。</p>
 */
public record RagChatRequest(
        Long conversationId,

        @NotBlank(message = "问题不能为空")
        String question,

        @Min(value = 1, message = "topK 至少为 1")
        @Max(value = 20, message = "topK 不能大于 20")
        Integer topK,

        @Valid
        RagChatFilterRequest filters
) {
}
