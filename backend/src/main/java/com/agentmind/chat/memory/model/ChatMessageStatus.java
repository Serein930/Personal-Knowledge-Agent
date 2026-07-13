package com.agentmind.chat.memory.model;

/**
 * 会话消息生成状态。
 *
 * <p>用户消息创建后直接完成；助手消息先进入等待状态，再单向流转到完成、失败或取消。
 * 只有完成状态的消息允许进入后续提示词上下文。</p>
 */
public enum ChatMessageStatus {

    PENDING,

    COMPLETED,

    FAILED,

    CANCELLED
}
