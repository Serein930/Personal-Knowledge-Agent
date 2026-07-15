package com.agentmind.study.flashcard.fsrs.model;

import java.time.OffsetDateTime;

/**
 * 单张卡片的 FSRS 内部状态快照。
 *
 * <p>负载使用第三方库提供的 JSON 序列化格式保存，领域层不直接依赖其字段布局。
 * 模式版本由本项目维护，算法版本用于判断未来升级时是否需要迁移或重建。</p>
 */
public record FsrsCardSnapshot(
        Long ownerUserId,
        Long workspaceId,
        Long flashcardId,
        String algorithmVersion,
        int schemaVersion,
        long profileVersion,
        String payload,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
