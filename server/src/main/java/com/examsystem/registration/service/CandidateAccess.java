package com.examsystem.registration.service;

import com.examsystem.auth.mapper.AuthMapper;
import com.examsystem.auth.model.AuthData;
import com.examsystem.auth.service.PermissionService;
import com.examsystem.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 本人身份入口；写入在事务内重新锁定账号，防止过滤器检查后账号被停用。 */
@Service
public class CandidateAccess {
    private final PermissionService permissions;
    private final AuthMapper accounts;

    public CandidateAccess(PermissionService permissions, AuthMapper accounts) {
        this.permissions = permissions;
        this.accounts = accounts;
    }

    public AuthData.User current() {
        var principal = permissions.principal();
        return validate(principal, accounts.user(principal.userId()));
    }

    /** 仅在资料或报名写事务中调用；随后锁资料、学校和考试资源。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuthData.User lockCurrent() {
        var principal = permissions.principal();
        return validate(principal, accounts.lockUser(principal.userId()));
    }

    private AuthData.User validate(AuthData.Principal principal, AuthData.User user) {
        if (user == null
                || !"ACTIVE".equals(user.status())
                || user.sessionVersion() != principal.sessionVersion()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_REQUIRED", "登录已失效，请重新登录");
        }
        if (!"CANDIDATE".equals(user.accountKind())) throw ApiException.forbidden();
        return user;
    }
}
