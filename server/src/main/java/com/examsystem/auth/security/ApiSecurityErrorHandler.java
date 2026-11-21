package com.examsystem.auth.security;

import com.examsystem.common.error.ApiErrors;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/** 安全过滤链的 JSON 错误出口；此阶段的拒绝不会进入 ControllerAdvice。 */
@Component
public class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiSecurityErrorHandler.class);
    private final ApiErrors errors;

    public ApiSecurityErrorHandler(ApiErrors errors) {
        this.errors = errors;
    }

    /** 未登录返回可关联的 JSON 401，避免跳转到 HTML 登录页。 */
    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException error)
            throws IOException {
        LOG.warn("event=security_request_rejected code=SESSION_REQUIRED");
        errors.write(request, response, 401, "SESSION_REQUIRED", "请先登录");
    }

    /** 区分 CSRF 失效与普通越权；不输出安全异常消息、Cookie 或令牌。 */
    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException error)
            throws IOException {
        boolean invalidCsrf = error instanceof CsrfException;
        String code = invalidCsrf ? "CSRF_INVALID" : "FORBIDDEN";
        LOG.warn("event=security_request_rejected code={}", code);
        errors.write(request, response, 403, code, invalidCsrf ? "请求凭证已失效，请刷新后重试" : "没有此操作权限");
    }
}
