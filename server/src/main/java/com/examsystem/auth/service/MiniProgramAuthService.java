package com.examsystem.auth.service;

import com.examsystem.auth.dto.MiniProgramDtos;
import com.examsystem.auth.entity.MiniProgramSession;
import com.examsystem.auth.entity.WechatIdentity;
import com.examsystem.auth.mapper.AuthMapper;
import com.examsystem.auth.mapper.MiniProgramSessionMapper;
import com.examsystem.auth.mapper.WebCredentialMapper;
import com.examsystem.auth.mapper.WechatIdentityMapper;
import com.examsystem.auth.model.AuthData;
import com.examsystem.auth.model.MiniProgramSessionIdentity;
import com.examsystem.common.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 绑定、会话签发和凭据增设；调用方先在事务外完成微信换码及 Web 密码认证。 */
@Service
public class MiniProgramAuthService {
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final int SESSION_HOURS = 24;

    private final AuthService auth;
    private final AuthMapper accounts;
    private final WebCredentialMapper credentials;
    private final WechatIdentityMapper identities;
    private final MiniProgramSessionMapper sessions;
    private final WechatLoginClient wechat;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public MiniProgramAuthService(
            AuthService auth,
            AuthMapper accounts,
            WebCredentialMapper credentials,
            WechatIdentityMapper identities,
            MiniProgramSessionMapper sessions,
            WechatLoginClient wechat,
            Clock clock) {
        this.auth = auth;
        this.accounts = accounts;
        this.credentials = credentials;
        this.identities = identities;
        this.sessions = sessions;
        this.wechat = wechat;
        this.clock = clock;
    }

    // region 微信与考生账号关联

    /** 已绑定的微信使用原考生账号；未绑定时由用户选择关联或创建。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MiniProgramDtos.Session login(byte[] openId) {
        WechatIdentity identity = identities.findByOpenId(wechat.appId(), openId);
        if (identity == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "MINI_ACCOUNT_REQUIRED", "请关联已有网页账号或创建考生账号");
        }
        return issue(
                identity.getId(), requireCandidate(accounts.lockUser(identity.getCandidateId())));
    }

    /** Web 密码已经验证，再锁定账号并确认版本，两端沿用原考生 ID。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MiniProgramDtos.Session link(byte[] openId, AuthData.User verified) {
        AuthData.User user = requireCandidate(accounts.lockUser(verified.id()));
        if (user.sessionVersion() != verified.sessionVersion()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "账号状态已变化，请重试");
        }
        WechatIdentity binding = identities.findByOpenId(wechat.appId(), openId);
        if (binding != null) {
            if (binding.getCandidateId() != user.id()) {
                throw bindingConflict();
            }
            return issue(binding.getId(), user);
        }
        if (identities.candidateHasBinding(user.id(), wechat.appId())) {
            throw bindingConflict();
        }
        return issue(bind(user.id(), openId), user);
    }

    /** 首次从小程序注册时，考生、资料、微信身份和会话同事务创建。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MiniProgramDtos.Session register(byte[] openId, String displayName) {
        if (identities.findByOpenId(wechat.appId(), openId) != null) {
            throw bindingConflict();
        }
        long candidateId = auth.createCandidate(displayName);
        return issue(bind(candidateId, openId), requireCandidate(accounts.user(candidateId)));
    }

    private long bind(long candidateId, byte[] openId) {
        WechatIdentity binding = new WechatIdentity();
        binding.setCandidateId(candidateId);
        binding.setAppId(wechat.appId());
        binding.setOpenId(openId);
        binding.setBoundAt(now());
        // 账号行锁串行化同一考生的请求，数据库唯一键处理跨考生的微信绑定冲突。
        try {
            identities.insert(binding);
        } catch (DuplicateKeyException conflict) {
            throw bindingConflict();
        }
        return binding.getId();
    }

    // endregion

    // region 会话与本人 Web 凭据

    public MiniProgramSessionIdentity authenticate(String token) {
        if (!wechat.isConfigured() || !TOKEN.matcher(token).matches()) {
            return null;
        }
        return sessions.findActive(digest(token), wechat.appId(), now());
    }

    public MiniProgramDtos.Candidate me(MiniProgramSessionIdentity session) {
        return describe(requireCurrent(session, accounts.user(session.candidateId())));
    }

    /** 锁定账号后再次校验会话，串行处理退出和增设凭据，保留既有密码。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MiniProgramDtos.Candidate addWebCredential(
            MiniProgramSessionIdentity session, String identifier, String password) {
        AuthData.User user = requireCurrent(session, accounts.lockUser(session.candidateId()));
        if (!sessions.isActive(session.sessionId(), now())) {
            throw sessionRequired();
        }
        if (credentials.selectById(user.id()) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "WEB_CREDENTIAL_EXISTS", "此考生账号已有网页登录凭据");
        }
        auth.addWebCredential(user.id(), identifier, password);
        return describe(user);
    }

    @Transactional
    public void logout(MiniProgramSessionIdentity session) {
        // 和增设凭据采用相同的锁顺序。
        accounts.lockUser(session.candidateId());
        sessions.revoke(session.sessionId(), now());
    }

    private MiniProgramDtos.Session issue(long bindingId, AuthData.User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime issuedAt = now();

        MiniProgramSession session = new MiniProgramSession();
        session.setWechatIdentityId(bindingId);
        session.setSessionVersion(user.sessionVersion());
        session.setTokenDigest(digest(token));
        session.setIssuedAt(issuedAt);
        session.setExpiresAt(issuedAt.plusHours(SESSION_HOURS));
        sessions.insert(session);
        return new MiniProgramDtos.Session(
                token, session.getExpiresAt().atOffset(ZoneOffset.UTC), describe(user));
    }

    private MiniProgramDtos.Candidate describe(AuthData.User user) {
        return new MiniProgramDtos.Candidate(
                Long.toString(user.id()),
                user.displayName(),
                credentials.selectById(user.id()) != null);
    }

    private static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(
                clock.instant().truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
    }

    private static AuthData.User requireCurrent(
            MiniProgramSessionIdentity session, AuthData.User account) {
        AuthData.User user = requireCandidate(account);
        if (user.sessionVersion() != session.sessionVersion()) {
            throw sessionRequired();
        }
        return user;
    }

    private static AuthData.User requireCandidate(AuthData.User user) {
        if (user == null
                || !"CANDIDATE".equals(user.accountKind())
                || !"ACTIVE".equals(user.status())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_UNAVAILABLE", "考生账号暂不可用");
        }
        return user;
    }

    private static ApiException sessionRequired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_REQUIRED", "请重新登录");
    }

    private static ApiException bindingConflict() {
        return new ApiException(
                HttpStatus.CONFLICT, "WECHAT_BINDING_CONFLICT", "微信或网页账号已有绑定，请使用原绑定登录");
    }
    // endregion
}
