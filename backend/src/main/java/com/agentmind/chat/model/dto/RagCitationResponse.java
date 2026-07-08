package com.agentmind.chat.model.dto;

/**
 * RAG 引用来源响应 DTO。
 *
 * <p>每条引用对应一个被检索命中的文档 chunk。前端展示 citation 时应让用户看到
 * 文档标题、原文片段和相似度分数，从而降低“模型胡说”的风险。</p>
 */
public record RagCitationResponse(
        Long documentId,
        String title,
        Long chunkId,
        String excerpt,
        double score
) {
}
