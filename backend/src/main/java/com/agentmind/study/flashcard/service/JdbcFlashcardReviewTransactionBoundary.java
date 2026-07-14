package com.agentmind.study.flashcard.service;

import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL 模式复习事务边界。
 *
 * <p>卡片版本条件更新和不可变复习记录必须同时提交或同时回滚，避免调度已经变化但缺少评分历史。</p>
 */
@Component
@ConditionalOnProperty(prefix = "agentmind.agent.persistence", name = "store", havingValue = "jdbc")
public class JdbcFlashcardReviewTransactionBoundary implements FlashcardReviewTransactionBoundary {

    private final TransactionTemplate transactionTemplate;

    public JdbcFlashcardReviewTransactionBoundary(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T execute(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }
}
