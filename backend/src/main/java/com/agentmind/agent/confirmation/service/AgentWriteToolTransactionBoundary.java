package com.agentmind.agent.confirmation.service;

import java.util.function.Supplier;

/**
 * 已确认写工具的事务边界端口。
 *
 * <p>确认单状态、工具审计和业务数据写入都从该边界进入。当前内存适配器保证单进程临界区语义，
 * 后续数据库实现可使用事务模板替换，并保持确认应用服务不变。</p>
 */
public interface AgentWriteToolTransactionBoundary {

    <T> T execute(Supplier<T> action);
}
