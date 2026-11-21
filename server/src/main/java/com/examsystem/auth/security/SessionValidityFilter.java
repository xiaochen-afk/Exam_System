package com.examsystem.auth.security;

import com.examsystem.auth.mapper.AuthMapper;
import com.examsystem.auth.model.AuthData;
import com.examsystem.common.error.ApiErrors;
import com.examsystem.portal.security.PublicPortalAccess;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 每请求校验账号状态/会话版本并刷新动作；仅注册到安全链，避免作为 Servlet Filter 执行两次。 */
public class SessionValidityFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(SessionValidityFilter.class);
    private final AuthMapper mapper;
    private final ApiErrors errors;

    public SessionValidityFilter(AuthMapper mapper, ApiErrors errors) {
        this.mapper = mapper;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current != null && current.getPrincipal() instanceof AuthData.Principal principal) {
            AuthData.User user = mapper.user(principal.userId());
            if (user == null
                    || !"ACTIVE".equals(user.status())
                    || user.sessionVersion() != principal.sessionVersion()) {
                if (request.getSession(false) != null) {
                    request.getSession(false).invalidate();
                }
                SecurityContextHolder.clearContext();
                LOG.warn(
                        "event=web_session_invalidated userId={} reason={}",
                        principal.userId(),
                        invalidReason(user));
                // 清理旧会话后，公开门户仍允许匿名阅读；私人接口继续按原口径返回 401。
                if (PublicPortalAccess.matches(request)) {
                    chain.doFilter(request, response);
                    return;
                }
                errors.write(request, response, 401, "SESSION_REQUIRED", "登录已失效，请重新登录");
                return;
            }
            // 会话只证明身份；权限每次读取最新授权，撤销后不能沿用登录时的快照。
            var authorities =
                    mapper.actions(user.id()).stream()
                            .map(AuthData.GrantAction::permissionCode)
                            .distinct()
                            .map(SimpleGrantedAuthority::new)
                            .toList();
            SecurityContextHolder.getContext()
                    .setAuthentication(
                            UsernamePasswordAuthenticationToken.authenticated(
                                    principal, null, authorities));
        }
        chain.doFilter(request, response);
    }

    private static String invalidReason(AuthData.User user) {
        if (user == null) {
            return "ACCOUNT_MISSING";
        }
        return "ACTIVE".equals(user.status()) ? "SESSION_VERSION_CHANGED" : "ACCOUNT_INACTIVE";
    }
}
