package com.agentmind.chat.model;

/**
 * 检索增强生成模型调用状态。
 *
 * <p>状态枚举用于区分调用开始、调用成功、调用失败、失败后降级返回和流式调用取消。后续落库后，
 * 评估与可观测页面可以基于该字段统计真实模型稳定性。</p>
 */
public enum RagModelCallStatus {

    STARTED,

    SUCCEEDED,

    FAILED,

    FALLBACK,

    CANCELLED
}
