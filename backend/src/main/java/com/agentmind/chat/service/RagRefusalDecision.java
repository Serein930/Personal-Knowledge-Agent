package com.agentmind.chat.service;

/**
 * 检索增强生成回答前的拒答判断结果。
 *
 * <p>拒答结果由检索质量决定，回答生成器只负责执行该结果。这样后续无论使用模拟生成器还是真实模型，
 * 都能遵守同一套“资料不足不强答”的产品规则。</p>
 */
public record RagRefusalDecision(
        boolean shouldRefuse,
        String reason
) {
}
