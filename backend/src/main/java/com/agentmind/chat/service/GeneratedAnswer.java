package com.agentmind.chat.service;

import com.agentmind.chat.model.dto.TokenUsageResponse;

/**
 * 回答生成结果。
 *
 * <p>结果中保留生成内容和令牌用量。模拟生成器暂时保持用量为零，真实模型适配器后续可以填充
 * 模型供应商返回的统计信息。</p>
 */
public record GeneratedAnswer(
        String content,
        TokenUsageResponse usage
) {
}
