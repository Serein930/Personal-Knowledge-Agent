package com.agentmind.study.flashcard.model;

import java.time.OffsetDateTime;

/**
 * 间隔重复算法的输入与输出状态。
 *
 * <p>算法只处理调度数据，不依赖数据库实体、HTTP 请求或用户上下文，因此可以使用固定时间进行纯单元测试，
 * 也可以在后续新增 FSRS 实现时保持应用服务不变。</p>
 */
public record StudyFlashcardSchedule(
        StudyFlashcardStatus status,
        int repetitionCount,
        int intervalDays,
        double easeFactor,
        int lapseCount,
        OffsetDateTime dueAt,
        OffsetDateTime lastReviewedAt
) {
}
