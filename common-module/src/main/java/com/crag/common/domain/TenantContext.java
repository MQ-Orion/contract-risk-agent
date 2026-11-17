package com.crag.common.domain;

import com.crag.common.api.ErrorCode;

/**
 * 租户上下文：租户标识只能由服务端从令牌解析后注入（规范 10.3），Controller 禁止自行解析。
 *
 * <p>调用方通过 {@link #require()} 获取租户；缺失即视为未认证，禁止回退到默认租户。</p>
 *
 * <p>跨线程语义：基于 ThreadLocal，不会自动传递到新线程或线程池任务。异步入口必须先显式
 * {@code set(...)} 再执行（见 {@code AsyncParseTaskDispatcher}），否则 {@link #require()} 抛未认证。
 * 这里选择“缺失即失败”而不是回退默认租户：回退会把越权读写成静默成功，比直接失败危险得多（规范 4.4、10.3）。</p>
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    /**
     * 设置当前租户。
     *
     * @param tenantId 租户标识
     */
    public static void set(long tenantId) {
        CURRENT.set(tenantId);
    }

    /**
     * 当前租户，可能为空。
     *
     * @return 租户标识或 null
     */
    public static Long current() {
        return CURRENT.get();
    }

    /**
     * 必须存在的租户；不存在抛未认证。
     *
     * @return 租户标识
     */
    public static long require() {
        Long tenantId = CURRENT.get();
        if (tenantId == null) {
            throw BizException.of(ErrorCode.UNAUTHENTICATED, "tenant context missing");
        }
        return tenantId;
    }

    /**
     * 清理当前租户。
     */
    public static void clear() {
        CURRENT.remove();
    }
}
