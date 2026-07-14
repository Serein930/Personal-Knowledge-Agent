package com.agentmind.chat.service;

import com.agentmind.chat.model.dto.TokenUsageResponse;
import com.agentmind.chat.model.dto.RagAnswerGenerationMetadataResponse;
import com.agentmind.agent.audit.model.dto.AgentToolCallSummaryResponse;
import java.util.List;

/**
 * 回答生成结果。
 *
 * <p>结果中保留生成内容、令牌用量和生成元数据。模拟生成器暂时保持用量为零，真实模型适配器
 * 后续可以填充模型供应商返回的统计信息。</p>
 */
public record GeneratedAnswer(
        String content,
        TokenUsageResponse usage,
        RagAnswerGenerationMetadataResponse metadata,
        List<AgentToolCallSummaryResponse> toolCalls
) {

    public GeneratedAnswer(
            String content,
            TokenUsageResponse usage,
            RagAnswerGenerationMetadataResponse metadata
    ) {
        this(content, usage, metadata, List.of());
    }
}
