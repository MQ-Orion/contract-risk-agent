package com.crag.common.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 租户解析器：租户标识必须由服务端从令牌等可信来源解析（规范 10.3）。
 *
 * <p>实现类不得读取请求体或查询参数中的租户字段。</p>
 */
public interface TenantResolver {

    /**
     * 解析当前请求的租户。
     *
     * @param request 当前请求，允许为空（后台任务场景）
     * @return 租户标识；无法解析时返回 null，由业务侧按未认证处理
     */
    Long resolveTenantId(HttpServletRequest request);
}
