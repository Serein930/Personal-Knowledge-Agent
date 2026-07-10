package com.agentmind.knowledge.model.dto;

/**
 * 单个命中文档片段的检索结果。
 *
 * <p>当前分数来自本地向量库的余弦相似度。后续接入数据库向量扩展后可以保持同一响应结构，
 * 只替换分数来源。</p>
 */
public record KnowledgeSearchResultResponse(
        String chunkId,
        Long documentId,
        int chunkSequence,
        String headingPath,
        String content,
        double score
) {
}
