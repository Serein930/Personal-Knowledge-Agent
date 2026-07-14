package com.agentmind.agent.confirmation.service;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * 本地开发阶段的写工具事务边界。
 *
 * <p>内存仓储不具备数据库回滚能力，因此本实现通过公平锁保证确认状态迁移和业务写入不会交叉执行。
 * 接入持久化后应替换为真正的数据库事务，实现异常时整体回滚。</p>
 */
@Component
public class InMemoryAgentWriteToolTransactionBoundary implements AgentWriteToolTransactionBoundary {

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
