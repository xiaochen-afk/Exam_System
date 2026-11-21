package com.examsystem.auth.service;

import com.examsystem.auth.mapper.AuthMapper;
import com.examsystem.auth.model.AuthData;
import com.examsystem.common.error.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 每请求加载最新内部身份和逐条授权；写入首先锁操作者，与撤权使用同一账号锁。 */
@Service
public class StaffAccessService {
    private final PermissionService permissions;
    private final AuthMapper accounts;

    public StaffAccessService(PermissionService permissions, AuthMapper accounts) {
        this.permissions = permissions;
        this.accounts = accounts;
    }

    public Actor reader() {
        return actor(false);
    }

    public Actor writer() {
        return actor(true);
    }

    private Actor actor(boolean lock) {
        var principal = permissions.principal();
        var user = lock ? accounts.lockUser(principal.userId()) : accounts.user(principal.userId());
        if (user == null
                || !"ACTIVE".equals(user.status())
                || user.sessionVersion() != principal.sessionVersion())
            throw new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_REQUIRED", "登录已失效，请重新登录");
        if (!"STAFF".equals(user.accountKind())) throw ApiException.forbidden();
        return new Actor(user.id(), accounts.actions(user.id()));
    }

    /** 动作必须和实际资源出现在同一条有效授权内；系统管理员不享有隐式考务权限。 */
    public record Actor(long id, List<AuthData.GrantAction> grants) {
        public boolean catalog(String action) {
            return grants.stream()
                    .anyMatch(
                            g ->
                                    action.equals(g.permissionCode())
                                            && "ORGANIZER".equals(g.roleCode())
                                            && "CATALOG".equals(g.scopeType()));
        }

        public void requireCatalog(String action) {
            if (!catalog(action)) throw ApiException.forbidden();
        }

        public void requireAction(String action) {
            if (grants.stream().noneMatch(g -> action.equals(g.permissionCode())))
                throw ApiException.forbidden();
        }

        public boolean project(String action, long project) {
            return grants.stream()
                    .anyMatch(
                            g ->
                                    action.equals(g.permissionCode())
                                            && "ORGANIZER".equals(g.roleCode())
                                            && "PROJECT".equals(g.scopeType())
                                            && Long.valueOf(project).equals(g.projectId()));
        }

        public boolean session(String action, long session, long project) {
            return project(action, project)
                    || grants.stream()
                            .anyMatch(
                                    g ->
                                            action.equals(g.permissionCode())
                                                    && "SESSION".equals(g.scopeType())
                                                    && Long.valueOf(session).equals(g.sessionId()));
        }

        public void requireProject(String action, long project) {
            if (!project(action, project)) throw ApiException.notFound();
        }

        public void requireSession(String action, long session, long project) {
            if (!session(action, session, project)) throw ApiException.notFound();
        }
    }
}
