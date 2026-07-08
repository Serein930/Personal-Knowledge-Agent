package com.agentmind.chat.model.dto;

/**
 * 模型 Token 用量响应 DTO。
 *
 * <p>该结构用于后续成本统计、评估观测和接口限流。Stage 2 只定义契约，
 * 真实值由模型调用适配器在后续阶段填充。</p>
 */
public record TokenUsageResponse(
        int promptTokens,
        int completionTokens,
        int totalTokens
) {
}
