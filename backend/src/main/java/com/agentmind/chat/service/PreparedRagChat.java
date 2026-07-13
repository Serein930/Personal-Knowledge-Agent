package com.agentmind.chat.service;

import com.agentmind.chat.model.dto.RagCitationResponse;
import com.agentmind.chat.model.dto.RagRetrievalContextResponse;
import java.util.List;

/**
 * 完成检索和提示词构造后的问答准备结果。
 *
 * <p>同步问答和流式问答共享该结构，确保两种接口使用完全相同的知识空间检索、
 * 引用顺序、拒答策略和提示词版本，避免两条链路逐渐产生行为差异。</p>
 */
public record PreparedRagChat(
        Long conversationId,
        Long messageId,
        RagRetrievalContextResponse retrievalContext,
        List<RagCitationResponse> citations,
        AnswerGenerationRequest generationRequest
) {
}
