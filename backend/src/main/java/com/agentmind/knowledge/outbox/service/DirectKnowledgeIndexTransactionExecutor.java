package com.agentmind.knowledge.outbox.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 内存模式无需数据库事务，直接执行索引动作。 */
@Component
@ConditionalOnProperty(prefix = "agentmind.knowledge-index.outbox", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DirectKnowledgeIndexTransactionExecutor implements KnowledgeIndexTransactionExecutor {

    @Override
    public void execute(Runnable action) {
        action.run();
    }
}
