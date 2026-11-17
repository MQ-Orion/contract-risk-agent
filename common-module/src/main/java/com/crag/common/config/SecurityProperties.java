package com.crag.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 安全相关配置（规范 10.3、15.2：配置必须有默认值、单位与说明）。
 */
@ConfigurationProperties(prefix = "crag.security")
public class SecurityProperties {

    /**
     * 租户来源：static 表示开发期固定租户，token 表示由令牌解析。
     * 生产环境必须为 token；未接入认证前保持 static 会打印告警。
     */
    private String tenantSource = "static";

    /**
     * 静态租户来源下使用的租户标识，仅限开发联调。
     */
    private long devTenantId = 1L;

    /**
     * 静态操作人标识，仅限开发联调；接入认证后由令牌解析。
     */
    private long devUserId = 1L;

    /**
     * 是否信任上游传递的 X-Trace-Id 头。关闭时一律生成新链路标识。
     */
    private boolean trustIncomingTraceId = true;

    public String getTenantSource() {
        return tenantSource;
    }

    public void setTenantSource(String tenantSource) {
        this.tenantSource = tenantSource;
    }

    public long getDevTenantId() {
        return devTenantId;
    }

    public void setDevTenantId(long devTenantId) {
        this.devTenantId = devTenantId;
    }

    public long getDevUserId() {
        return devUserId;
    }

    public void setDevUserId(long devUserId) {
        this.devUserId = devUserId;
    }

    public boolean isTrustIncomingTraceId() {
        return trustIncomingTraceId;
    }

    public void setTrustIncomingTraceId(boolean trustIncomingTraceId) {
        this.trustIncomingTraceId = trustIncomingTraceId;
    }
}
