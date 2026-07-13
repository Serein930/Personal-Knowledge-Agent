package com.agentmind.chat.service;

/**
 * 客户端断开或会话超时后用于中止流式生成的内部异常。
 *
 * <p>该异常不会经过全局接口异常处理器，而是在流式编排服务中转换为会话结束动作。</p>
 */
public class RagStreamTerminatedException extends RuntimeException {

    private final RagStreamTerminationReason reason;

    public RagStreamTerminatedException(RagStreamTerminationReason reason) {
        super(reason.message());
        this.reason = reason;
    }

    public RagStreamTerminationReason reason() {
        return reason;
    }
}
