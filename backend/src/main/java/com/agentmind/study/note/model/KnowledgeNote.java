package com.agentmind.study.note.model;

import java.time.OffsetDateTime;

/**
 * 用户知识笔记模型。
 *
 * <p>笔记始终同时记录用户和知识空间归属。即使客户端知道笔记编号，也不能脱离这两个边界查询。</p>
 */
public record KnowledgeNote(
        Long id,
        Long ownerUserId,
        Long workspaceId,
        Long sourceConversationId,
        String title,
        String content,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
