package com.examsystem.auth.service;

import com.examsystem.auth.dto.AdminRequests;
import com.examsystem.auth.mapper.AdminMapper;
import com.examsystem.auth.mapper.PermissionMapper;
import com.examsystem.common.error.ApiException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 授权条目的规则及整体替换；调用方负责锁定操作者/目标、校验 users.write 并保存审计。 */
@Service
public class GrantService {
    private static final int MAX_GRANTS = 100;
    private static final int MAX_PERMISSIONS = 24;
    private final AdminMapper admin;
    private final PermissionMapper scopes;
    private final PermissionService permissions;

    public GrantService(AdminMapper admin, PermissionMapper scopes, PermissionService permissions) {
        this.admin = admin;
        this.scopes = scopes;
        this.permissions = permissions;
    }

    // region 公开规则与写入

    /** 空数组表示撤销全部；null 在创建时非法，更新时由调用方解释为“不修改”。 */
    public void validate(List<AdminRequests.GrantRequest> grants) {
        if (grants == null || grants.size() > MAX_GRANTS) {
            throw ApiException.invalid("须提供 grants 数组，最多为 100 条；空数组表示无授权");
        }
        Set<GrantKey> keys = new HashSet<>();
        // 只在本次校验中复用角色上限，避免逐条重复查询；不跨请求缓存可变授权字典。
        Map<String, List<AdminMapper.Ceiling>> ceilingsByRole = new HashMap<>();
        for (AdminRequests.GrantRequest grant : grants) {
            validateActionsPresent(grant);
            validateScope(grant);
            validateDelegation(grant);
            validateTarget(grant);
            validateCeiling(grant, ceilingsByRole);
            if (!keys.add(
                    new GrantKey(
                            grant.roleCode(),
                            grant.scopeType(),
                            grant.projectId(),
                            grant.sessionId()))) {
                throw ApiException.invalid("授权范围不能重复");
            }
        }
    }

    /** 替换已校验的授权；必须加入持有账号锁的业务事务，不能独立提交半份授权。 历史记录保留，通过撤销与恢复复用原授权 ID，再整体替换所属动作。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void replace(long target, List<AdminRequests.GrantRequest> grants) {
        long actor = permissions.actorId();
        admin.revokeAll(actor, target);
        for (AdminRequests.GrantRequest grant : grants) {
            admin.upsertGrant(
                    actor,
                    target,
                    grant.roleCode(),
                    grant.scopeType(),
                    grant.projectId(),
                    grant.sessionId());
            long id = admin.grantId(target, grant.roleCode(), grant.scopeType(), scopeKey(grant));
            admin.clearGrantPermissions(id);
            for (String permission : grant.permissionCodes()) {
                admin.grantPermission(id, grant.roleCode(), grant.scopeType(), permission);
            }
        }
    }

    // endregion

    // region 单条授权校验：结构、委派边界、目标存在性、动作上限

    private static void validateActionsPresent(AdminRequests.GrantRequest grant) {
        if (grant == null
                || grant.permissionCodes() == null
                || grant.permissionCodes().isEmpty()
                || grant.permissionCodes().size() > MAX_PERMISSIONS) {
            throw ApiException.invalid("每条授权须包含有效动作");
        }
    }

    private static void validateScope(AdminRequests.GrantRequest grant) {
        if (grant.scopeType() == null) {
            throw ApiException.invalid("角色与授权范围不匹配");
        }
        boolean valid =
                switch (grant.scopeType()) {
                    case "SYSTEM" -> "SYSTEM_ADMIN".equals(grant.roleCode()) && noResourceId(grant);
                    case "CATALOG" -> "ORGANIZER".equals(grant.roleCode()) && noResourceId(grant);
                    case "PROJECT" ->
                            "ORGANIZER".equals(grant.roleCode())
                                    && positive(grant.projectId())
                                    && grant.sessionId() == null;
                    case "SESSION" ->
                            ("ORGANIZER".equals(grant.roleCode())
                                            || "SITE_MANAGER".equals(grant.roleCode()))
                                    && positive(grant.sessionId())
                                    && grant.projectId() == null;
                    default -> false;
                };
        if (!valid) {
            throw ApiException.invalid("角色与授权范围不匹配");
        }
    }

    private void validateDelegation(AdminRequests.GrantRequest grant) {
        if ("SYSTEM".equals(grant.scopeType())) {
            permissions.requireSystem("admins.manage");
        }
        // admins.manage 只能由受控初始化授予，HTTP 不能借助角色编辑传播此动作。
        if (grant.permissionCodes().contains("admins.manage")) {
            throw ApiException.forbidden();
        }
    }

    private void validateTarget(AdminRequests.GrantRequest grant) {
        boolean missingProject =
                "PROJECT".equals(grant.scopeType()) && admin.projectExists(grant.projectId()) == 0;
        boolean missingSession =
                "SESSION".equals(grant.scopeType()) && scopes.session(grant.sessionId()) == null;
        if (missingProject || missingSession) {
            throw ApiException.invalid("授权目标不存在");
        }
    }

    private void validateCeiling(
            AdminRequests.GrantRequest grant,
            Map<String, List<AdminMapper.Ceiling>> ceilingsByRole) {
        List<String> allowed =
                ceilingsByRole.computeIfAbsent(grant.roleCode(), admin::ceilings).stream()
                        .filter(ceiling -> grant.scopeType().equals(ceiling.scopeType()))
                        .map(AdminMapper.Ceiling::permissionCode)
                        .toList();
        if (!allowed.containsAll(grant.permissionCodes())
                || new HashSet<>(grant.permissionCodes()).size()
                        != grant.permissionCodes().size()) {
            throw ApiException.invalid("动作超出该角色与范围的权限上限");
        }
    }

    private static boolean noResourceId(AdminRequests.GrantRequest grant) {
        return grant.projectId() == null && grant.sessionId() == null;
    }

    private static boolean positive(Long id) {
        return id != null && id > 0;
    }

    private static long scopeKey(AdminRequests.GrantRequest grant) {
        if (grant.projectId() != null) {
            return grant.projectId();
        }
        return grant.sessionId() == null ? 0 : grant.sessionId();
    }

    private record GrantKey(String role, String scope, Long projectId, Long sessionId) {}

    // endregion
}
