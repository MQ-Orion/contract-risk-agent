package com.crag.common.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 开发期静态操作人解析器：只用于本地联调，生产由令牌解析器替换。
 */
public class StaticUserResolver implements UserResolver {

    private final long userId;

    /**
     * 构造解析器。
     *
     * @param userId 固定用户标识
     */
    public StaticUserResolver(long userId) {
        this.userId = userId;
    }

    @Override
    public Long resolveUserId(HttpServletRequest request) {
        return userId;
    }
}
