package com.agentmind.knowledge.outbox.model;

/** OpenSearch 索引需要执行的幂等操作。 */
public enum KnowledgeIndexOutboxOperation {
    UPSERT,
    DELETE
}
