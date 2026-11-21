package com.examsystem.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.examsystem.auth.assembler.AdminResponseAssembler;
import com.examsystem.auth.dto.AdminResponses;
import com.examsystem.auth.entity.Role;
import com.examsystem.auth.mapper.AdminMapper;
import com.examsystem.auth.mapper.AuthMapper;
import com.examsystem.auth.mapper.RoleMapper;
import com.examsystem.auth.model.AuthData;
import com.examsystem.auth.validation.AccountRules;
import com.examsystem.common.dto.PageResponse;
import com.examsystem.common.query.PageQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 内部账号和角色目录查询；只读事务内批量加载依赖数据，响应组装交给 Assembler。 */
@Service
public class AdminDirectoryService {
    private final AdminMapper admin;
    private final AuthMapper accounts;
    private final RoleMapper roleMapper;
    private final PermissionService permissions;

    public AdminDirectoryService(
            AdminMapper admin,
            AuthMapper accounts,
            RoleMapper roleMapper,
            PermissionService permissions) {
        this.admin = admin;
        this.accounts = accounts;
        this.roleMapper = roleMapper;
        this.permissions = permissions;
    }

    /** 要求系统范围 users.read；capabilities 仅供界面提示，写入仍由命令服务重新判权。 */
    @Transactional(readOnly = true)
    public PageResponse<AdminResponses.Staff> staff(
            String keyword, String status, int page, int size) {
        permissions.requireSystem("users.read");
        PageQuery query = new PageQuery(page, size, keyword);
        String filter = status == null ? "" : status;
        if (!filter.isEmpty()) {
            AccountRules.status(filter);
        }
        var rows = admin.staff(query.keyword(), filter, query.size(), query.offset());
        Map<Long, List<AuthData.GrantAction>> grants = loadGrants(rows);
        boolean canWrite = permissions.hasSystem("users.write");
        boolean canManageAdmins = permissions.hasSystem("admins.manage");
        long actorId = permissions.actorId();
        List<AdminResponses.Staff> items = new ArrayList<>();
        for (AdminMapper.StaffRow row : rows) {
            var actions = grants.getOrDefault(row.id(), List.of());
            boolean editable =
                    canWrite
                            && row.id() != actorId
                            && (!hasAdministratorRole(actions) || canManageAdmins);
            items.add(AdminResponseAssembler.staff(row, actions, editable));
        }
        return query.response(items, admin.staffCount(query.keyword(), filter));
    }

    /** 要求系统范围 roles.read；一次批量读取当前页角色的权限上限，避免 N+1 查询。 */
    @Transactional(readOnly = true)
    public PageResponse<AdminResponses.Role> roles(int page, int size) {
        permissions.requireSystem("roles.read");
        PageQuery query = new PageQuery(page, size, "");
        Page<Role> result =
                roleMapper.selectPage(
                        new Page<>(query.page(), query.size()),
                        Wrappers.<Role>lambdaQuery().orderByAsc(Role::getCode));
        var rows = result.getRecords();
        Map<String, List<AdminMapper.Ceiling>> ceilings =
                admin.ceilingsForRoles(rows.stream().map(Role::getCode).toList()).stream()
                        .collect(
                                Collectors.groupingBy(
                                        AdminMapper.RoleCeiling::roleCode,
                                        Collectors.mapping(
                                                AdminMapper.RoleCeiling::ceiling,
                                                Collectors.toList())));
        var items =
                rows.stream()
                        .map(
                                row ->
                                        AdminResponseAssembler.role(
                                                row,
                                                ceilings.getOrDefault(row.getCode(), List.of())))
                        .toList();
        return query.response(items, result.getTotal());
    }

    private Map<Long, List<AuthData.GrantAction>> loadGrants(List<AdminMapper.StaffRow> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        return accounts
                .actionsForUsers(rows.stream().map(AdminMapper.StaffRow::id).toList())
                .stream()
                .collect(
                        Collectors.groupingBy(
                                AuthData.OwnedGrantAction::staffId,
                                Collectors.mapping(
                                        AuthData.OwnedGrantAction::action, Collectors.toList())));
    }

    /** users.write 即可加载授权表单，不要求管理员同时拥有考试读取或 roles.read 权限。 */
    @Transactional(readOnly = true)
    public AdminResponses.GrantOptions grantOptions() {
        permissions.requireSystem("users.write");
        boolean manageAdmins = permissions.hasSystem("admins.manage");
        var roles =
                roleMapper
                        .selectList(Wrappers.<Role>lambdaQuery().orderByAsc(Role::getCode))
                        .stream()
                        .filter(role -> manageAdmins || !"SYSTEM_ADMIN".equals(role.getCode()))
                        .toList();
        Map<String, List<AdminMapper.Ceiling>> ceilings =
                admin.ceilingsForRoles(roles.stream().map(Role::getCode).toList()).stream()
                        .filter(ceiling -> !"admins.manage".equals(ceiling.permissionCode()))
                        .collect(
                                Collectors.groupingBy(
                                        AdminMapper.RoleCeiling::roleCode,
                                        Collectors.mapping(
                                                AdminMapper.RoleCeiling::ceiling,
                                                Collectors.toList())));
        var views =
                roles.stream()
                        .map(
                                role ->
                                        AdminResponseAssembler.role(
                                                role,
                                                ceilings.getOrDefault(role.getCode(), List.of())))
                        .toList();
        return new AdminResponses.GrantOptions(
                views, admin.projectOptions(), admin.sessionOptions());
    }

    private static boolean hasAdministratorRole(List<AuthData.GrantAction> actions) {
        return actions.stream().anyMatch(action -> "SYSTEM_ADMIN".equals(action.roleCode()));
    }
}
