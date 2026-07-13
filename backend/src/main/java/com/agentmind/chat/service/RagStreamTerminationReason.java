package com.agentmind.chat.service;

/**
 * 流式会话被外部终止的原因。
 */
public enum RagStreamTerminationReason {

    CLIENT_DISCONNECTED("STREAM_CLIENT_DISCONNECTED", "客户端已断开流式连接"),
    TIMED_OUT("STREAM_TIMEOUT", "流式回答已超时");

    private final String code;
    private final String message;

    RagStreamTerminationReason(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
