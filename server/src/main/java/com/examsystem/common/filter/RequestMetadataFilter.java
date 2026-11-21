package com.examsystem.common.filter;

import com.examsystem.common.error.ApiErrors;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 最外层 HTTP 元数据处理：生成请求编号、管理 MDC 生命周期，并禁止私人 API 响应缓存。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestMetadataFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(RequestMetadataFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = ApiErrors.requestId(request);
        response.setHeader("X-Request-ID", requestId);
        if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
            response.setHeader("Cache-Control", "no-store");
        }
        String previousRequestId = MDC.get("requestId");
        long started = System.nanoTime();
        MDC.put("requestId", requestId);
        try {
            chain.doFilter(request, response);
            // 请求详情仅记录状态和耗时，不记录 URL、查询参数、请求体或 Cookie。
            LOG.debug(
                    "event=http_request_completed status={} durationMs={}",
                    response.getStatus(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        } finally {
            // Servlet 线程会被复用，必须清理本次请求编号，并保留调用方原有上下文。
            if (previousRequestId == null) {
                MDC.remove("requestId");
            } else {
                MDC.put("requestId", previousRequestId);
            }
        }
    }
}
