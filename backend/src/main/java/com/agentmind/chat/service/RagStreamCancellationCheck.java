package com.agentmind.chat.service;

/**
 * 流式生成过程的取消检查端口。
 *
 * <p>传输层通过该端口把客户端断开和超时信号传给模型适配器，生成器无需依赖网页响应对象。</p>
 */
@FunctionalInterface
public interface RagStreamCancellationCheck {

    void check();
}
