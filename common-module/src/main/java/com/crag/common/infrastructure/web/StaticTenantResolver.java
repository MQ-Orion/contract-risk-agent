package com.crag.common.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 开发期静态租户解析器：从服务端配置读固定租户，不接受任何客户端输入。
 *
 * <p>该实现只用于本地开发与联调。生产 profile 下 {@code crag.security.tenant-source=token}，
 * 由接入认证模块后的令牌解析器替换；启动时若仍为 static 会打印告警。</p>
 */
public class StaticTenantResolver implements TenantResolver {

    private static final Logger log = LoggerFactory.getLogger(StaticTenantResolver.class);

    private final long tenantId;

    /**
     * 构造解析器。
     *
     * @param tenantId 固定租户标识
     */
    public StaticTenantResolver(long tenantId) {
        this.tenantId = tenantId;
        log.warn("静态租户来源已启用，tenantId={}；该模式仅限开发联调，生产必须切换为令牌解析（规范 10.3）", tenantId);
    }

    @Override
    public Long resolveTenantId(HttpServletRequest request) {
        return tenantId;
    }
}
