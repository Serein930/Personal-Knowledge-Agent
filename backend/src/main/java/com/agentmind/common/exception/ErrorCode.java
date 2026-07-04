package com.agentmind.common.exception;

/**
 * 后端统一错误码。
 *
 * <p>错误码使用稳定字符串，便于前端根据 code 做提示和分支处理。</p>
 */
public enum ErrorCode {
    BAD_REQUEST("BAD_REQUEST", "请求参数不合法"),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "资源不存在"),
    INTERNAL_ERROR("INTERNAL_ERROR", "服务内部错误");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
