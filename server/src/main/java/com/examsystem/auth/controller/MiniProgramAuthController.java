package com.examsystem.auth.controller;

import com.examsystem.auth.dto.MiniProgramDtos;
import com.examsystem.auth.model.AuthData;
import com.examsystem.auth.model.MiniProgramSessionIdentity;
import com.examsystem.auth.security.AuthenticationThrottle;
import com.examsystem.auth.security.MiniProgramAccess;
import com.examsystem.auth.service.AuthService;
import com.examsystem.auth.service.MiniProgramAuthService;
import com.examsystem.auth.service.WechatLoginClient;
import com.examsystem.auth.validation.AccountRules;
import com.examsystem.common.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 小程序登录、绑定和退出入口。微信换码与密码认证在账号事务前完成。 */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping(MiniProgramAccess.ROOT)
public class MiniProgramAuthController {
    private static final Logger LOG = LoggerFactory.getLogger(MiniProgramAuthController.class);
    private final MiniProgramAuthService sessions;
    private final WechatLoginClient wechat;
    private final AuthService auth;
    private final AuthenticationThrottle throttle;

    public MiniProgramAuthController(
            MiniProgramAuthService sessions,
            WechatLoginClient wechat,
            AuthService auth,
            AuthenticationThrottle throttle) {
        this.sessions = sessions;
        this.wechat = wechat;
        this.auth = auth;
        this.throttle = throttle;
    }

    // region 微信身份建立

    @PostMapping("/sessions")
    ResponseEntity<MiniProgramDtos.Session> login(
            @RequestBody MiniProgramDtos.Login body, HttpServletRequest request) {
        throttle.check("mini-login", request.getRemoteAddr(), null);
        MiniProgramDtos.Session session = sessions.login(wechat.exchange(body.code()));
        LOG.info("event=mini_login_succeeded userId={}", session.candidate().id());
        return signedIn(session);
    }

    @PostMapping("/account-links")
    ResponseEntity<MiniProgramDtos.Session> link(
            @RequestBody MiniProgramDtos.Link body, HttpServletRequest request) {
        // 密码尝试和网页登录共享额度，防止从关联入口绕过限流。
        throttle.check(
                "login", request.getRemoteAddr(), AccountRules.identifier(body.identifier()));
        byte[] openId = wechat.exchange(body.code());
        AuthData.User user = auth.authenticate(body.identifier(), body.password(), "CANDIDATE");
        MiniProgramDtos.Session session = sessions.link(openId, user);
        LOG.info("event=mini_account_link_succeeded userId={}", user.id());
        return signedIn(session);
    }

    @PostMapping("/candidate-accounts")
    ResponseEntity<MiniProgramDtos.Session> register(
            @RequestBody MiniProgramDtos.Registration body, HttpServletRequest request) {
        throttle.check("register", request.getRemoteAddr(), null);
        String displayName = AccountRules.displayName(body.displayName());
        MiniProgramDtos.Session session =
                sessions.register(wechat.exchange(body.code()), displayName);
        LOG.info("event=mini_candidate_registered userId={}", session.candidate().id());
        return signedIn(session);
    }

    // endregion

    // region 已登录会话

    @GetMapping("/me")
    MiniProgramDtos.Candidate me(Authentication authentication) {
        return sessions.me(current(authentication));
    }

    @PostMapping("/web-credentials")
    ResponseEntity<MiniProgramDtos.Candidate> addWebCredential(
            @RequestBody MiniProgramDtos.WebCredential body,
            Authentication authentication,
            HttpServletRequest request) {
        MiniProgramSessionIdentity session = current(authentication);
        throttle.check(
                "mini-web-credential",
                request.getRemoteAddr(),
                Long.toString(session.candidateId()));
        MiniProgramDtos.Candidate candidate =
                sessions.addWebCredential(session, body.identifier(), body.password());
        LOG.info("event=mini_web_credential_created userId={}", session.candidateId());
        return ResponseEntity.status(201).body(candidate);
    }

    @DeleteMapping("/sessions/current")
    ResponseEntity<Void> logout(Authentication authentication) {
        MiniProgramSessionIdentity session = current(authentication);
        sessions.logout(session);
        LOG.info("event=mini_logout_succeeded userId={}", session.candidateId());
        return ResponseEntity.noContent().build();
    }

    private static MiniProgramSessionIdentity current(Authentication authentication) {
        if (authentication != null
                && authentication.getDetails() instanceof MiniProgramSessionIdentity session) {
            return session;
        }
        throw ApiException.forbidden();
    }

    private static ResponseEntity<MiniProgramDtos.Session> signedIn(
            MiniProgramDtos.Session session) {
        return ResponseEntity.created(URI.create(MiniProgramAccess.ROOT + "/sessions/current"))
                .cacheControl(CacheControl.noStore())
                .body(session);
    }
    // endregion
}
