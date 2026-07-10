package com.agentmind.chat.model.dto;

import com.agentmind.document.model.DocumentSourceType;
import java.util.List;

/**
 * RAG 问答的可选检索过滤条件。
 *
 * <p>当前阶段只实现按知识空间隔离的向量检索，标签和来源类型先作为接口契约保留，
 * 后续接入文档元数据持久化后再真正参与过滤。</p>
 */
public record RagChatFilterRequest(
        List<String> tags,
        List<DocumentSourceType> sourceTypes
) {
}
