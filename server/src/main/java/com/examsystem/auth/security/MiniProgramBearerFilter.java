package com.examsystem.auth.security;

import com.examsystem.auth.model.AuthData;
import com.examsystem.auth.model.MiniProgramSessionIdentity;
import com.examsystem.auth.service.MiniProgramAuthService;
import com.examsystem.common.error.ApiErrors;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 将通过校验的小程序会话转换为请求内的统一考生身份。 */
public class MiniProgramBearerFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(MiniProgramBearerFilter.class);
    private static final String PREFIX = "Bearer ";
    private final MiniProgramAuthService sessions;
    private final ApiErrors errors;

    public MiniProgramBearerFilter(MiniProgramAuthService sessions, ApiErrors errors) {
        this.sessions = sessions;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !MiniProgramAccess.isProtected(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var headers = request.getHeaders(HttpHeaders.AUTHORIZATION);
        String header = headers.hasMoreElements() ? headers.nextElement() : "";
        if (!headers.hasMoreElements()
                && header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            try {
                MiniProgramSessionIdentity session =
                        sessions.authenticate(header.substring(PREFIX.length()));
                if (session != null) {
                    var principal =
                            new AuthData.Principal(session.candidateId(), session.sessionVersion());
                    var authentication =
                            UsernamePasswordAuthenticationToken.authenticated(
                                    principal, null, List.of());
                    authentication.setDetails(session);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (DataAccessException failure) {
                LOG.error(
                        "event=mini_session_lookup_failed exceptionType={}",
                        failure.getClass().getName());
                errors.write(request, response, 500, "INTERNAL_ERROR", "服务暂时无法处理请求");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
