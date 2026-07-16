package com.agentmind.knowledge.outbox.model;

/** 事务消息从待处理到成功或死信的状态。 */
public enum KnowledgeIndexOutboxStatus {
    PENDING,
    PROCESSING,
    RETRY,
    COMPLETED,
    DEAD
}
