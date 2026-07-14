package com.agentmind.study.flashcard.model;

/**
 * 复习卡片学习状态。
 */
public enum StudyFlashcardStatus {
    /** 新创建、尚未完成首次复习。 */
    NEW,
    /** 正在建立初始记忆，复习间隔较短。 */
    LEARNING,
    /** 已进入稳定的间隔复习阶段。 */
    REVIEW,
    /** 用户暂停复习，不进入到期卡片列表。 */
    SUSPENDED
}
