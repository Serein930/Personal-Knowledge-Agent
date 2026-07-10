package com.agentmind.chat.model.dto;

/**
 * 模型令牌用量摘要。
 *
 * <p>模拟生成阶段不会调用真实模型，因此数值保持为零。后续真实模型适配器应根据供应商返回的
 * 用量元数据填充该结构。</p>
 */
public record TokenUsageResponse(
        int promptTokens,
        int completionTokens,
        int totalTokens
) {
}
