package com.agentmind.chat.model.dto;

import java.util.List;

/**
 * 用于回答生成的检索上下文。
 *
 * <p>提示词上下文字段故意保持为纯文本，并使用引用编号组织内容，方便测试断言和后续模型提示词拼装。
 * 该上下文只能包含检索到的个人知识，不能包含编造事实。</p>
 */
public record RagRetrievalContextResponse(
        String question,
        int topK,
        String promptContext,
        List<RagCitationResponse> citations
) {
}
