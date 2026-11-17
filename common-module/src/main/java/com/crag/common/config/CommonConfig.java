package com.crag.common.config;

import com.crag.common.domain.SnowflakeIdGenerator;
import com.crag.common.infrastructure.web.StaticTenantResolver;
import com.crag.common.infrastructure.web.StaticUserResolver;
import com.crag.common.infrastructure.web.TenantResolver;
import com.crag.common.infrastructure.web.UserResolver;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 基础组件装配：统一时钟、雪花标识与租户解析器。
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class CommonConfig {

    /**
     * 统一时钟：业务代码禁止直接取系统时间（规范 3.10）。存储与判定用 UTC。
     *
     * @return UTC 时钟
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * 雪花标识生成器。
     *
     * @param workerId 工作节点编号，由部署环境注入
     * @return 生成器
     */
    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator(
            @Value("${crag.id.worker-id:0}") long workerId) {
        return new SnowflakeIdGenerator(workerId);
    }

    /**
     * 租户解析器：当前仅支持开发期静态租户；token 模式需随认证模块落地，未就绪时启动失败而非静默降级。
     *
     * @param properties 安全配置
     * @return 租户解析器
     */
    @Bean
    public TenantResolver tenantResolver(SecurityProperties properties) {
        String source = properties.getTenantSource();
        if ("token".equalsIgnoreCase(source)) {
            throw new IllegalStateException("crag.security.tenant-source=token 需要认证模块提供的令牌解析器，"
                    + "该能力随第 8–9 周审核任务与权限里程碑落地；当前请使用 static（仅开发联调）");
        }
        if (!"static".equalsIgnoreCase(source)) {
            throw new IllegalStateException("未知的 crag.security.tenant-source: " + source);
        }
        return new StaticTenantResolver(properties.getDevTenantId());
    }

    /**
     * 操作人解析器：与租户同源，随认证模块切换为令牌解析。
     *
     * @param properties 安全配置
     * @return 操作人解析器
     */
    @Bean
    public UserResolver userResolver(SecurityProperties properties) {
        if ("static".equalsIgnoreCase(properties.getTenantSource())) {
            return new StaticUserResolver(properties.getDevUserId());
        }
        throw new IllegalStateException("当前仅实现 static 身份来源，token 模式随认证里程碑落地（规范 10.3）");
    }
}
