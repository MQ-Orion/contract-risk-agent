package com.crag.common.domain;

import com.crag.common.api.ErrorCode;
import java.util.UUID;

/**
 * 链路标识上下文：贯穿上传、解析、切分、检索、规则、模型、报告全链路（规范 16.1）。
 *
 * <p>链路标识存放在 {@link ThreadLocal} 中，由入口过滤器写入，出口清理，防止线程池复用串号。</p>
 */
public final class TraceContext {

    /** 存放链路标识的 MDC 键名。 */
    public static final String MDC_KEY = "traceId";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TraceContext() {
    }

    /**
     * 生成新的链路标识。
     *
     * @return 32 位十六进制链路标识
     */
    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 当前链路标识；不存在时返回空串，保证响应字段非空。
     *
     * @return 链路标识
     */
    public static String current() {
        String value = CURRENT.get();
        return value == null ? "" : value;
    }

    /**
     * 设置链路标识。
     *
     * @param traceId 链路标识
     */
    public static void set(String traceId) {
        CURRENT.set(traceId);
    }

    /**
     * 清理链路标识。
     */
    public static void clear() {
        CURRENT.remove();
    }

    /**
     * 统一取 UTC 毫秒时间戳，避免以本地时区写入响应（规范 3.10）。
     *
     * @return UTC 毫秒
     */
    public static long nowMillis() {
        return System.currentTimeMillis();
    }
}
