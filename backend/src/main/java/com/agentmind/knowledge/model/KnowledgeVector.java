package com.agentmind.knowledge.model;

import java.time.OffsetDateTime;

/**
 * 由单个文档片段生成的向量记录。
 *
 * <p>该模型刻意携带知识空间和文档编号，因为所有检索都必须限定在用户知识空间内。
 * 接入数据库向量扩展后，这些字段应成为向量字段旁边可查询的普通列。</p>
 */
public record KnowledgeVector(
        String id,
        Long workspaceId,
        Long documentId,
        String chunkId,
        int chunkSequence,
        String headingPath,
        String content,
        float[] embedding,
        OffsetDateTime createdAt
) {
}
