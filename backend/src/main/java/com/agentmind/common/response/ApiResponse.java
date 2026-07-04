package com.agentmind.common.response;

import java.time.OffsetDateTime;

/**
 * 统一 API 响应结构。
 *
 * <p>前端可以稳定依赖该结构读取业务状态、提示信息和数据载荷。
 * traceId 在第一阶段暂时允许为空，后续接入链路追踪或日志 MDC 后再统一填充。</p>
 */
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String traceId,
        OffsetDateTime timestamp
) {

    private static final String SUCCESS_CODE = "SUCCESS";

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, "success", data, null, OffsetDateTime.now());
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(SUCCESS_CODE, message, data, null, OffsetDateTime.now());
    }

    public static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(code, message, null, null, OffsetDateTime.now());
    }
}
