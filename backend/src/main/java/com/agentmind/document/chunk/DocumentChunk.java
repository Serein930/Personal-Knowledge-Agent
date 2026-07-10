package com.agentmind.document.chunk;

/**
 * 从文档文本中切分出的知识片段。
 *
 * <p>该模型保留原文位置和标题路径，便于后续向量记录携带引用元数据。
 * 这些字段能让检索增强生成回答清楚展示答案来自哪个文档片段。</p>
 */
public record DocumentChunk(
        String id,
        Long documentId,
        int sequence,
        String headingPath,
        String content,
        int charStart,
        int charEnd
) {
}
