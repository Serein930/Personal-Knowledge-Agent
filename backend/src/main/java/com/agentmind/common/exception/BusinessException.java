package com.agentmind.common.exception;

/**
 * 业务异常基类。
 *
 * <p>控制层和服务层遇到可预期业务失败时抛出该异常，
 * 由全局异常处理器转换为统一接口响应。</p>
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
