package com.agentmind.chat.service;

import com.agentmind.chat.model.dto.RagAnswerGenerationMetadataResponse;
import com.agentmind.chat.model.dto.TokenUsageResponse;
import com.agentmind.agent.audit.model.dto.AgentToolCallSummaryResponse;
import java.util.List;

/**
 * 流式回答生成完成后的摘要。
 *
 * <p>正文已经通过增量回调发送，因此结果只保存回答长度、令牌用量和生成元数据，
 * 避免在应用服务中再次复制完整回答。</p>
 */
public record StreamingGeneratedAnswer(
        int answerLength,
        TokenUsageResponse usage,
        RagAnswerGenerationMetadataResponse metadata,
        List<AgentToolCallSummaryResponse> toolCalls
) {

    public StreamingGeneratedAnswer(
            int answerLength,
            TokenUsageResponse usage,
            RagAnswerGenerationMetadataResponse metadata
    ) {
        this(answerLength, usage, metadata, List.of());
    }
}
