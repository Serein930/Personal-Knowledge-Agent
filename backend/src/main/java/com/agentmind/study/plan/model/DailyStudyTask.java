package com.agentmind.study.plan.model;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 每日学习计划中的一项可执行任务。
 *
 * <p>任务保存当日生成时选中的卡片编号，避免第二天卡片状态变化后历史计划内容漂移。
 * 主题和文档编号是解释字段，前端可以明确告诉用户任务为什么被推荐。</p>
 */
public record DailyStudyTask(
        Long id,
        Long planId,
        Long ownerUserId,
        Long workspaceId,
        DailyStudyTaskType type,
        DailyStudyTaskPriority priority,
        String topic,
        Long sourceDocumentId,
        int targetCardCount,
        String reason,
        List<Long> flashcardIds,
        OffsetDateTime createdAt
) {

    public DailyStudyTask {
        flashcardIds = List.copyOf(flashcardIds);
    }

    public DailyStudyTask withIdentity(Long generatedId, Long generatedPlanId) {
        return new DailyStudyTask(
                generatedId, generatedPlanId, ownerUserId, workspaceId, type, priority,
                topic, sourceDocumentId, targetCardCount, reason, flashcardIds, createdAt
        );
    }
}
