package com.crag.agent.rule.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 规则引擎配置（规范 8.1、8.2）。
 *
 * <p>阈值一律配置化，不写死在代码里（规范 3.10）；默认值与规范一致：刷新间隔 30 秒（要求 ≤60 秒），
 * 求值超时 1 秒。</p>
 */
@ConfigurationProperties(prefix = "crag.rule")
public class RuleProperties {

    /** 缓存刷新间隔（毫秒），规范 8.1 要求不超过 60 秒。 */
    private long refreshMillis = 30_000L;

    /** 启动后首次刷新延迟（毫秒），避免与数据库迁移抢启动窗口。 */
    private long initialDelayMillis = 3_000L;

    /** 单条表达式求值超时（毫秒），规范 8.2 要求 1 秒。 */
    private long evalTimeoutMillis = 1_000L;

    public long getRefreshMillis() {
        return refreshMillis;
    }

    public void setRefreshMillis(long refreshMillis) {
        this.refreshMillis = refreshMillis;
    }

    public long getInitialDelayMillis() {
        return initialDelayMillis;
    }

    public void setInitialDelayMillis(long initialDelayMillis) {
        this.initialDelayMillis = initialDelayMillis;
    }

    public long getEvalTimeoutMillis() {
        return evalTimeoutMillis;
    }

    public void setEvalTimeoutMillis(long evalTimeoutMillis) {
        this.evalTimeoutMillis = evalTimeoutMillis;
    }
}
