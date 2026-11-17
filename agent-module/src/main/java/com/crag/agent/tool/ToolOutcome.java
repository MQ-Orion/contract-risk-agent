package com.crag.agent.tool;

/**
 * 工具执行结果（规范 7.1：失败必须返回结构化原因，禁止把裸异常抛给模型）。
 *
 * @param ok        是否成功
 * @param data      成功时的数据
 * @param errorCode 失败时的错误码（业务码或工具自定义码）
 * @param message   失败原因
 * @param <T>       数据类型
 */
public record ToolOutcome<T>(boolean ok, T data, String errorCode, String message) {

    /**
     * 成功结果。
     *
     * @param data 数据
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ToolOutcome<T> ok(T data) {
        return new ToolOutcome<>(true, data, null, null);
    }

    /**
     * 失败结果。
     *
     * @param errorCode 错误码
     * @param message   原因
     * @param <T>       类型
     * @return 结果
     */
    public static <T> ToolOutcome<T> failure(String errorCode, String message) {
        return new ToolOutcome<>(false, null, errorCode, message);
    }
}
