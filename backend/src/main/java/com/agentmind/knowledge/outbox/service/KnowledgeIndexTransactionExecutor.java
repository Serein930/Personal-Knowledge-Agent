package com.agentmind.knowledge.outbox.service;

/** 封装索引写入事务边界，便于内存模式与数据库模式平滑切换。 */
public interface KnowledgeIndexTransactionExecutor {

    void execute(Runnable action);
}
