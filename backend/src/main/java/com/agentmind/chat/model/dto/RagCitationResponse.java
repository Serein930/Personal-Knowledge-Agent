package com.agentmind.chat.model.dto;

/**
 * RAG 响应中的引用来源。
 *
 * <p>每条引用对应一个被检索命中的文档片段。片段编号使用字符串，是为了兼容当前类似 `10-0`
 * 的稳定逻辑编号，并在向量库记录中保留同一个值用于溯源。</p>
 */
public record RagCitationResponse(
        int index,
        Long documentId,
        String documentTitle,
        String chunkId,
        int chunkSequence,
        String headingPath,
        String excerpt,
        double score
) {
}
