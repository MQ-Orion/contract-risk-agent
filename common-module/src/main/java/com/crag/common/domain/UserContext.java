package com.crag.common.domain;

import com.crag.common.api.ErrorCode;

/**
 * 当前操作人上下文：与租户上下文同源，由认证环节写入（规范 10.3、11.6）。
 *
 * <p>审计留痕需要“谁在何时做了什么”，因此操作人不得由请求体传入。</p>
 */
public final class UserContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private UserContext() {
    }

    /**
     * 设置当前操作人。
     *
     * @param userId 用户标识
     */
    public static void set(long userId) {
        CURRENT.set(userId);
    }

    /**
     * 当前操作人，可能为空。
     *
     * @return 用户标识或 null
     */
    public static Long current() {
        return CURRENT.get();
    }

    /**
     * 必须存在的操作人。
     *
     * @return 用户标识
     */
    public static long require() {
        Long userId = CURRENT.get();
        if (userId == null) {
            throw BizException.of(ErrorCode.UNAUTHENTICATED, "user context missing");
        }
        return userId;
    }

    /**
     * 清理当前操作人。
     */
    public static void clear() {
        CURRENT.remove();
    }
}
