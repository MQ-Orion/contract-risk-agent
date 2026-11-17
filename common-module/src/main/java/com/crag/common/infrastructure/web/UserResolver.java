package com.crag.common.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 操作人解析器：与租户解析同源，来自服务端认证结果，禁止读取请求体（规范 10.3）。
 */
public interface UserResolver {

    /**
     * 解析当前请求的操作人。
     *
     * @param request 当前请求
     * @return 用户标识；无法解析时返回 null
     */
    Long resolveUserId(HttpServletRequest request);
}
