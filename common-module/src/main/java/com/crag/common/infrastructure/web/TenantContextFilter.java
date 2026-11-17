package com.crag.common.infrastructure.web;

import com.crag.common.domain.TenantContext;
import com.crag.common.domain.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 身份上下文过滤器：把服务端解析出的租户与操作人写入上下文，并在请求结束后清理（规范 10.3）。
 *
 * <p>调用链：Servlet 容器 → {@code TraceIdFilter}（最高优先级，先分配 traceId）→ 本过滤器 →
 * Controller → 应用服务 → MyBatis 租户拦截器读 {@link TenantContext}。本过滤器只写上下文，不校验身份。</p>
 *
 * <p>时序与失败语义：解析器返回 null 表示“本请求无需身份”，此时不写入、由后续 {@code require()} 失败；
 * 清理放在 finally，避免线程池复用线程时把上一个请求的租户带到下一个请求（规范 10.3、4.4）。
 * OPTIONS 预检不携带身份，直接放行，否则跨域预检会被判未认证。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantContextFilter extends OncePerRequestFilter {

    private final TenantResolver tenantResolver;
    private final UserResolver userResolver;

    /**
     * 构造过滤器。
     *
     * @param tenantResolver 租户解析器
     * @param userResolver   操作人解析器
     */
    public TenantContextFilter(TenantResolver tenantResolver, UserResolver userResolver) {
        this.tenantResolver = tenantResolver;
        this.userResolver = userResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            Long tenantId = tenantResolver.resolveTenantId(request);
            if (tenantId != null) {
                TenantContext.set(tenantId);
            }
            Long userId = userResolver.resolveUserId(request);
            if (userId != null) {
                UserContext.set(userId);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            UserContext.clear();
        }
    }
}
