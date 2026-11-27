package com.examsystem.auth.service;

import com.examsystem.auth.dto.AuthResponses;
import com.examsystem.auth.entity.UserAccount;
import com.examsystem.auth.mapper.AuthMapper;
import com.examsystem.auth.mapper.CandidateProfileMapper;
import com.examsystem.auth.mapper.WebCredentialMapper;
import com.examsystem.auth.model.AuthData;
import com.examsystem.auth.validation.AccountRules;
import com.examsystem.common.error.ApiException;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 公开注册、密码认证和身份读取；账号事务独立于 Controller 的 Cookie/CSRF 会话交互。 */
@Service
public class AuthService {
    // region 依赖与构造

    private final AuthMapper mapper;
    private final WebCredentialMapper credentials;
    private final CandidateProfileMapper profiles;
    private final PasswordEncoder encoder;
    private final String dummyHash;

    public AuthService(
            AuthMapper mapper,
            WebCredentialMapper credentials,
            CandidateProfileMapper profiles,
            PasswordEncoder encoder) {
        this.mapper = mapper;
        this.credentials = credentials;
        this.profiles = profiles;
        this.encoder = encoder;
        this.dummyHash = encoder.encode("unused-" + java.util.UUID.randomUUID());
    }

    // endregion

    // region 命令：考生注册

    /** 原子创建考生账号、凭据和空资料；标识冲突统一返回 409，不允许公开分配内部角色。 */
    @Transactional
    public long register(String identifier, String password, String displayName) {
        String normalized = AccountRules.identifier(identifier);
        String hash = encoder.encode(AccountRules.password(password));
        long id = createCandidate(displayName);
        saveWebCredential(id, normalized, hash);
        return id;
    }

    /** Web 与小程序共用考生及资料初始化，由调用方和认证记录一起提交。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public long createCandidate(String displayName) {
        UserAccount user = new UserAccount();
        user.setAccountKind("CANDIDATE");
        user.setDisplayName(AccountRules.displayName(displayName));
        mapper.insert(user);
        profiles.createForCandidate(user.getId());
        return user.getId();
    }

    /** 新增 Web 凭据使用相同的校验、密码编码和标识唯一性规则。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void addWebCredential(long userId, String identifier, String password) {
        String normalized = AccountRules.identifier(identifier);
        String hash = encoder.encode(AccountRules.password(password));
        saveWebCredential(userId, normalized, hash);
    }

    private void saveWebCredential(long userId, String identifier, String hash) {
        try {
            credentials.create(userId, identifier, hash);
        } catch (DuplicateKeyException conflict) {
            throw new ApiException(HttpStatus.CONFLICT, "IDENTIFIER_TAKEN", "此账号已被使用");
        }
    }

    // endregion

    // region 认证：凭据与登录入口校验

    /** portal 只选择登录入口，最终身份仍由账号类型决定；所有凭据失败返回相同错误。 */
    public AuthData.User authenticate(String identifier, String password, String portal) {
        String normalized = AccountRules.identifier(identifier);
        if (password == null
                || password.length() > 64
                || password.isEmpty()
                || !("CANDIDATE".equals(portal) || "STAFF".equals(portal))) {
            throw invalidCredentials();
        }
        AuthData.Credential credential = credentials.findForAuthentication(normalized);
        // 未找到账号也执行一次哈希比对，避免直接暴露账号是否存在。
        boolean correct =
                encoder.matches(
                        password, credential == null ? dummyHash : credential.passwordHash());
        AuthData.User user = credential == null ? null : mapper.user(credential.userId());
        if (!correct
                || user == null
                || !"ACTIVE".equals(user.status())
                || !portal.equals(user.accountKind())) {
            throw invalidCredentials();
        }
        return user;
    }

    // endregion

    // region 响应：身份与当前权限

    /** 白名单身份响应；角色和动作只供菜单展示，业务请求仍需检查实时授权范围。 */
    public AuthResponses.Identity identity(AuthData.User user) {
        List<AuthData.GrantAction> actions = mapper.actions(user.id());
        List<String> roles =
                "CANDIDATE".equals(user.accountKind())
                        ? List.of("CANDIDATE")
                        : actions.stream()
                                .map(
                                        a ->
                                                "SYSTEM_ADMIN".equals(a.roleCode())
                                                        ? "ADMIN"
                                                        : "EXAM_STAFF")
                                .distinct()
                                .sorted()
                                .toList();
        List<String> permissions =
                "CANDIDATE".equals(user.accountKind())
                        ? List.of()
                        : actions.stream()
                                .map(AuthData.GrantAction::permissionCode)
                                .distinct()
                                .sorted()
                                .toList();
        return new AuthResponses.Identity(
                Long.toString(user.id()), user.displayName(), roles, permissions);
    }

    // endregion

    // region 认证错误

    public static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "账号或密码错误，或账号不可用");
    }
    // endregion

}
