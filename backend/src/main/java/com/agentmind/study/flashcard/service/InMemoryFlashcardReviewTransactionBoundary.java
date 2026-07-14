package com.agentmind.study.flashcard.service;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 内存模式复习事务边界。
 *
 * <p>公平锁使卡片版本更新和复习记录写入作为一个临界区执行，用于提供与数据库事务一致的测试语义。</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "agentmind.agent.persistence",
        name = "store",
        havingValue = "memory",
        matchIfMissing = true
)
public class InMemoryFlashcardReviewTransactionBoundary implements FlashcardReviewTransactionBoundary {

    private final ReentrantLock lock = new ReentrantLock(true);

    @Override
    public <T> T execute(Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
