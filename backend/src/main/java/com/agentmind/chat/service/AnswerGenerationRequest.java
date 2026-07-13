package com.agentmind.chat.service;

import com.agentmind.chat.model.dto.RagCitationResponse;
import java.util.List;

/**
 * 回答生成请求。
 *
 * <p>该对象集中保存用户问题、检索上下文、最终生成提示词、引用来源和拒答判断。后续真实模型适配器
 * 可以直接把它转换为模型请求，测试环境则继续使用可重复的模拟实现。</p>
 */
public record AnswerGenerationRequest(
        Long workspaceId,
        String question,
        String promptVersion,
        String promptContext,
        String generationPrompt,
        List<RagCitationResponse> citations,
        RagRefusalDecision refusalDecision
) {
}
