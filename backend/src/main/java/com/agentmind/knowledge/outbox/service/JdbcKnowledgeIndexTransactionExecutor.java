package com.agentmind.knowledge.outbox.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 生产模式事务执行器，保证 pgvector 和 Outbox 写入同时提交或同时回滚。 */
@Component
@ConditionalOnProperty(prefix = "agentmind.knowledge-index.outbox", name = "enabled", havingValue = "true")
public class JdbcKnowledgeIndexTransactionExecutor implements KnowledgeIndexTransactionExecutor {

    private final TransactionTemplate transactionTemplate;

    public JdbcKnowledgeIndexTransactionExecutor(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void execute(Runnable action) {
        transactionTemplate.executeWithoutResult(status -> action.run());
    }
}
