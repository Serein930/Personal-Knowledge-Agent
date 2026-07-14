package com.agentmind.study.flashcard.model;

import java.time.OffsetDateTime;

/**
 * 复习卡片模型。
 *
 * <p>问题与答案构成最小复习单元，解释字段用于保存推理线索或易错点。每张卡片都保留用户、
 * 知识空间和写工具请求编号，支持权限隔离与数据库幂等。</p>
 */
public record StudyFlashcard(
        Long id,
        Long ownerUserId,
        Long workspaceId,
        Long sourceConversationId,
        String requestId,
        String question,
        String answer,
        String explanation,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
