package com.crag.common.api;

import com.crag.common.domain.TraceContext;

/**
 * 统一响应体：状态码、提示消息、数据体、链路标识、时间戳五个字段（规范 10.2）。
 *
 * @param code      业务状态码，0 为成功
 * @param message   面向用户的提示消息
 * @param data      数据体
 * @param traceId   链路标识
 * @param timestamp 服务端时间戳（UTC 毫秒）
 * @param <T>       数据体类型
 */
public record ApiResponse<T>(int code, String message, T data, String traceId, long timestamp) {

    /**
     * 成功响应。
     *
     * @param data 数据体
     * @param <T>  数据体类型
     * @return 统一响应
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.code(), ErrorCode.SUCCESS.message(), data,
                TraceContext.current(), TraceContext.nowMillis());
    }

    /**
     * 无数据体的成功响应。
     *
     * @param <T> 数据体类型
     * @return 统一响应
     */
    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    /**
     * 失败响应。消息固定取错误码的默认文案，避免内部细节外泄。
     *
     * @param errorCode 错误码
     * @param <T>       数据体类型
     * @return 统一响应
     */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.code(), errorCode.message(), null,
                TraceContext.current(), TraceContext.nowMillis());
    }
}
