package com.agentmind.agent.confirmation.service;

import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 基于 Spring 事务模板的写工具事务边界。
 *
 * <p>确认单状态迁移、工具审计与笔记或卡片写入共享同一数据库连接；任一步骤抛出异常时，
 * 当前事务整体回滚，避免确认单成功但业务数据缺失等部分成功状态。</p>
 */
@Component
@ConditionalOnProperty(prefix = "agentmind.agent.persistence", name = "store", havingValue = "jdbc")
public class JdbcAgentWriteToolTransactionBoundary implements AgentWriteToolTransactionBoundary {

    private final TransactionTemplate transactionTemplate;

    public JdbcAgentWriteToolTransactionBoundary(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T execute(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }
}
