package com.crag.common.infrastructure.web;

import com.crag.common.domain.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 链路标识过滤器：为每个请求建立链路标识并写入响应头（规范 16.1）。
 *
 * <p>为什么校验上游链路标识的形态：直接把请求头写进日志与响应，等于允许外部注入任意文本（换行可伪造日志行）。
 * 只接受 8–64 位十六进制，不合规则重新生成；这样跨系统串联与防注入两个目标同时成立（规范 3.7、16.1）。</p>
 *
 * <p>时序：本过滤器优先级最高，先于 {@code TenantContextFilter}，保证后者与业务日志都能拿到 traceId。
 * 清理放在 finally，线程池复用线程时不会把上一个请求的 traceId 带进下一个请求。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 外部链路标识的合法形态：8 至 64 位十六进制。 */
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8,64}$");

    /** 链路标识响应头。 */
    public static final String TRACE_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(TRACE_HEADER);
        String traceId = incoming != null && TRACE_ID_PATTERN.matcher(incoming).matches()
                ? incoming
                : TraceContext.newTraceId();
        TraceContext.set(traceId);
        MDC.put(TraceContext.MDC_KEY, traceId);
        response.setHeader(TRACE_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceContext.MDC_KEY);
            TraceContext.clear();
        }
    }
}
