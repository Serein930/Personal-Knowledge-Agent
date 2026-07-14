package com.agentmind.study.note.model.dto;

import java.time.OffsetDateTime;

/**
 * 知识笔记对外响应。
 */
public record KnowledgeNoteResponse(
        Long id,
        Long workspaceId,
        Long sourceConversationId,
        String title,
        String content,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
