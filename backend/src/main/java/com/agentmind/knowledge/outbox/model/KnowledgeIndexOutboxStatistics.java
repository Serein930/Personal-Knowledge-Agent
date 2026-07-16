package com.agentmind.knowledge.outbox.model;

/** 前端或运维接口使用的事务消息积压统计。 */
public record KnowledgeIndexOutboxStatistics(
        long pending,
        long processing,
        long retrying,
        long completed,
        long dead
) {
}
