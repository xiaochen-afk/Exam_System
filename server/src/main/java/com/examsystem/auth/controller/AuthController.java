package com.examsystem.auth.controller;

import com.examsystem.auth.dto.AuthRequests;
import com.examsystem.auth.dto.AuthResponses;
import com.examsystem.auth.mapper.AuthMapper;
import com.examsystem.auth.model.AuthData;
import com.examsystem.auth.security.AuthenticationThrottle;
import com.examsystem.auth.service.AuthService;
import com.examsystem.auth.validation.AccountRules;
import com.examsystem.common.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Web 认证 HTTP 入口：组织请求/响应、会话和 CSRF，账号规则及持久化由 AuthService 负责。 */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/v1")
public class AuthController {
    // region 依赖与构造

    private static final Logger LOG = LoggerFactory.getLogger(AuthController.class);
    private final AuthService service;
    private final AuthMapper mapper;
    private final AuthenticationThrottle throttle;
    private final SecurityContextRepository contexts;
    private final HttpSessionCsrfTokenRepository csrf;

    public AuthController(
            AuthService service,
            AuthMapper mapper,
            AuthenticationThrottle throttle,
            SecurityContextRepository contexts,
            HttpSessionCsrfTokenRepository csrf) {
        this.service = service;
        this.mapper = mapper;
        this.throttle = throttle;
        this.contexts = contexts;
        this.csrf = csrf;
    }

    // endregion

    // region 公开入口：CSRF 与考生注册

    /** 建立匿名会话并返回请求凭证；前端在登录轮换后须重新读取，令牌不得记入日志。 */
    @GetMapping("/csrf-tokens")
    Map<String, String> csrfToken(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return Map.of("token", token.getToken());
    }

    /** 仅创建考生账号，注册成功不自动登录；来源限流先于密码编码和数据库写入。 */
    @PostMapping("/candidate-accounts")
    ResponseEntity<Map<String, String>> register(
            @RequestBody AuthRequests.Registration body, HttpServletRequest request) {
        throttle.check("register", request.getRemoteAddr(), null);
        long id = service.register(body.identifier(), body.password(), body.displayName());
        // Service 事务已提交；日志只保留内部 ID，不输出注册资料和凭据。
        LOG.info("event=candidate_registered userId={}", id);
        return ResponseEntity.created(URI.create("/api/v1/candidate-accounts/" + id))
                .body(Map.of("id", Long.toString(id)));
    }

    // endregion

    // region 会话入口：登录、身份读取与退出

    /** 验证凭据后轮换会话 ID 和 CSRF，再显式保存业务身份，防止会话固定攻击。 */
    @PostMapping("/web-sessions")
    ResponseEntity<AuthResponses.Identity> login(
            @RequestBody AuthRequests.Login body,
            HttpServletRequest request,
            HttpServletResponse response) {
        throttle.check(
                "login", request.getRemoteAddr(), AccountRules.identifier(body.identifier()));
        AuthData.User user =
                service.authenticate(body.identifier(), body.password(), body.portal());
        Authentication authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        new AuthData.Principal(user.id(), user.sessionVersion()), null, List.of());
        // 自定义 JSON 登录须显式轮换会话 ID、废弃旧 CSRF 并保存 SecurityContext。
        new ChangeSessionIdAuthenticationStrategy()
                .onAuthentication(authentication, request, response);
        new CsrfAuthenticationStrategy(csrf).onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        AuthResponses.Identity identity = service.identity(user);
        LOG.info(
                "event=web_login_succeeded userId={} accountKind={}",
                user.id(),
                user.accountKind());
        return ResponseEntity.created(URI.create("/api/v1/web-sessions/current")).body(identity);
    }

    /** 当前身份只取服务端认证结果，不接受客户端传入 userId。 */
    @GetMapping("/me")
    AuthResponses.Identity me(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof AuthData.Principal principal)) {
            throw ApiException.forbidden();
        }
        return service.identity(mapper.user(principal.userId()));
    }

    /** 撤销当前会话并清除相同路径的 Cookie；匿名退出同样必须通过 CSRF 校验。 */
    @DeleteMapping("/web-sessions/current")
    ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {
        csrf.saveToken(null, request, response);
        SecurityContextLogoutHandler handler = new SecurityContextLogoutHandler();
        handler.setSecurityContextRepository(contexts);
        handler.logout(request, response, authentication);
        String path = request.getContextPath().isEmpty() ? "/" : request.getContextPath();
        response.addHeader(
                "Set-Cookie",
                ResponseCookie.from("JSESSIONID", "")
                        .httpOnly(true)
                        .secure(request.isSecure())
                        .sameSite("Lax")
                        .path(path)
                        .maxAge(0)
                        .build()
                        .toString());
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthData.Principal principal) {
            LOG.info("event=web_logout_succeeded userId={}", principal.userId());
        }
        return ResponseEntity.noContent().build();
    }
    // endregion

}
