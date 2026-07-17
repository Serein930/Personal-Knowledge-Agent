package com.agentmind.common.exception;

/**
 * 后端统一错误码。
 *
 * <p>错误码使用稳定字符串，便于前端根据错误码字段做提示和分支处理。</p>
 */
public enum ErrorCode {
    BAD_REQUEST("BAD_REQUEST", "请求参数不合法"),
    UNAUTHORIZED("UNAUTHORIZED", "请先完成身份认证"),
    FORBIDDEN("FORBIDDEN", "无权访问该资源"),
    RATE_LIMITED("RATE_LIMITED", "请求过于频繁"),
    DEPENDENCY_UNAVAILABLE("DEPENDENCY_UNAVAILABLE", "外部依赖暂时不可用"),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "资源不存在"),
    RESOURCE_CONFLICT("RESOURCE_CONFLICT", "资源状态冲突"),
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
