package com.crag.common.infrastructure.mybatis;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.crag.common.domain.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 租户隔离拦截器（规范 4.4）：查询、更新、删除由拦截器在 SQL 层强制注入租户条件。
 *
 * <p>租户值只能来自服务端上下文；上下文缺失直接抛未认证，禁止回退到默认租户。
 * 手写 SQL 的 Mapper（如向量条件 upsert）在方法上用 {@code @InterceptorIgnore(tenantLine = "true")}
 * 关闭自动注入，但必须在 SQL 中显式带租户条件——二者至少存在其一。</p>
 */
@Configuration
public class TenantLineConfig {

    /**
     * MyBatis-Plus 拦截器链。
     *
     * @return 拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                // 取值即校验：上下文缺失时 require() 抛未认证（10002），而不是拼出 tenant_id = null 让 SQL 返回空集。
                // 规范 4.4 要求“拦截器是唯一保障”，若这里回退默认租户，越权查询会变成静默成功。
                return new LongValue(TenantContext.require());
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                // 默认拒绝：任何表都注入租户条件。新增表若确需跨租户（如全局配置），必须显式改这里或方法级
                // @InterceptorIgnore，并在 ADR 登记用途与期限（规范 4.4）。白名单式放行会把遗漏变成越权。
                return false;
            }
        }));
        return interceptor;
    }
}
