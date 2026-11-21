package com.examsystem.auth.service;

import com.examsystem.auth.mapper.AuthMapper;
import com.examsystem.auth.mapper.PermissionMapper;
import com.examsystem.auth.model.AuthData;
import com.examsystem.common.error.ApiException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** 读取最新授权的判权入口；系统动作和资源动作分开判断，不跨请求缓存授权快照。 */
@Service
public class PermissionService {
    // region 依赖与构造

    private final AuthMapper mapper;
    private final PermissionMapper scopes;

    public PermissionService(AuthMapper mapper, PermissionMapper scopes) {
        this.mapper = mapper;
        this.scopes = scopes;
    }

    // endregion

    // region 当前操作者

    public long actorId() {
        return principal().userId();
    }

    /** 只接受服务端建立的业务身份，匿名身份及其他认证类型不能推导内部账号 ID。 */
    public AuthData.Principal principal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AuthData.Principal principal)) {
            throw ApiException.forbidden();
        }
        return principal;
    }

    // endregion

    // region 系统范围判权

    /** 动作必须属于 SYSTEM 范围；管理员身份本身不等价于所有业务权限。 */
    public boolean hasSystem(String action) {
        return mapper.actions(actorId()).stream()
                .anyMatch(
                        grant ->
                                action.equals(grant.permissionCode())
                                        && "SYSTEM".equals(grant.scopeType()));
    }

    public void requireSystem(String action) {
        if (!hasSystem(action)) {
            throw ApiException.forbidden();
        }
    }

    // endregion

    // region 项目与场次范围判权

    /** 缺失资源和范围外资源统一返回 404，避免利用判权接口枚举资源。 */
    public void requireSession(String action, long sessionId) {
        var scope = scopes.session(sessionId);
        if (scope == null || !allowsSession(actorId(), action, scope)) {
            throw ApiException.notFound();
        }
    }

    /** 同一条授权同时匹配动作与项目/场次，不把不同授权的动作和范围拼接。 */
    public boolean allowsSession(long actorId, String action, PermissionMapper.SessionScope scope) {
        // 动作和范围来自同一条授权，禁止把甲场次的动作与乙场次的范围拼接。
        return mapper.actions(actorId).stream()
                .anyMatch(
                        grant ->
                                action.equals(grant.permissionCode())
                                        && matchesSessionScope(grant, scope));
    }

    private static boolean matchesSessionScope(
            AuthData.GrantAction grant, PermissionMapper.SessionScope scope) {
        if ("PROJECT".equals(grant.scopeType())) {
            return Long.valueOf(scope.projectId()).equals(grant.projectId());
        }
        return "SESSION".equals(grant.scopeType())
                && Long.valueOf(scope.id()).equals(grant.sessionId());
    }
    // endregion

}
