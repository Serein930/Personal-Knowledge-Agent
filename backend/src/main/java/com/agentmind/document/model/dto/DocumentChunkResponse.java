package com.agentmind.document.model.dto;

/**
 * 临时片段预览接口使用的响应结构。
 *
 * <p>该结构用于让前端或开发者在片段持久化到数据库和向量库前，先验证解析器与切分器行为。</p>
 */
public record DocumentChunkResponse(
        String id,
        Long documentId,
        int sequence,
        String headingPath,
        String content,
        int charStart,
        int charEnd
) {
}
