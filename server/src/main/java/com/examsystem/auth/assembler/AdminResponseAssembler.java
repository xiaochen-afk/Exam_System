package com.examsystem.auth.assembler;

import com.examsystem.auth.dto.AdminResponses;
import com.examsystem.auth.entity.Role;
import com.examsystem.auth.mapper.AdminMapper;
import com.examsystem.auth.mapper.AdminReadMapper;
import com.examsystem.auth.model.AuthData;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 纯响应映射：不查询数据库、不做权限决策，集中维护字符串 ID、UTC 时间及字段白名单。 */
public final class AdminResponseAssembler {
    private AdminResponseAssembler() {}

    // region 授权及账号视图

    /** 将按动作展开的行恢复为逐条授权；保留查询顺序，供响应和审计摘要共同复用。 */
    public static List<AdminResponses.Grant> grants(List<AuthData.GrantAction> rows) {
        Map<Long, List<AuthData.GrantAction>> groups = new LinkedHashMap<>();
        for (AuthData.GrantAction row : rows) {
            groups.computeIfAbsent(row.grantId(), ignored -> new ArrayList<>()).add(row);
        }
        return groups.values().stream().map(AdminResponseAssembler::grant).toList();
    }

    private static AdminResponses.Grant grant(List<AuthData.GrantAction> rows) {
        AuthData.GrantAction first = rows.getFirst();
        return new AdminResponses.Grant(
                first.roleCode(),
                first.scopeType(),
                id(first.projectId()),
                id(first.sessionId()),
                rows.stream().map(AuthData.GrantAction::permissionCode).toList());
    }

    public static AdminResponses.Staff staff(
            AdminMapper.StaffRow row, List<AuthData.GrantAction> actions, boolean editable) {
        String roles =
                String.join(
                        "、",
                        actions.stream().map(AuthData.GrantAction::roleCode).distinct().toList());
        String scopes =
                String.join(
                        "、",
                        actions.stream()
                                .map(AdminResponseAssembler::scopeLabel)
                                .distinct()
                                .toList());
        return new AdminResponses.Staff(
                Long.toString(row.id()),
                row.displayName(),
                row.identifier(),
                row.status(),
                grants(actions),
                roles,
                scopes,
                editable ? List.of("EDIT") : List.of());
    }

    private static String scopeLabel(AuthData.GrantAction grant) {
        String target = "目录";
        if (grant.projectId() != null) {
            target = grant.projectId().toString();
        } else if (grant.sessionId() != null) {
            target = grant.sessionId().toString();
        }
        return grant.scopeType() + ":" + target;
    }

    // endregion

    // region 角色、场次及审计视图

    public static AdminResponses.Role role(Role row, List<AdminMapper.Ceiling> ceilings) {
        var scopes =
                ceilings.stream()
                        .map(
                                ceiling ->
                                        new AdminResponses.ScopePermission(
                                                ceiling.scopeType(),
                                                ceiling.permissionCode(),
                                                ceiling.permissionLabel()))
                        .toList();
        return new AdminResponses.Role(
                row.getCode(),
                row.getName(),
                row.getDescription(),
                ceilings.stream().map(AdminMapper.Ceiling::permissionCode).distinct().toList(),
                ceilings.stream().map(AdminMapper.Ceiling::permissionLabel).distinct().toList(),
                scopes);
    }

    public static AdminResponses.Session session(AdminReadMapper.SessionRow row) {
        return session(row, List.of());
    }

    /** 数据库存储 UTC；按钮能力由调用方结合当前授权和锁定状态计算。 */
    public static AdminResponses.Session session(
            AdminReadMapper.SessionRow row, List<String> capabilities) {
        return new AdminResponses.Session(
                Long.toString(row.id()),
                row.name(),
                Long.toString(row.subjectId()),
                row.subjectName(),
                Long.toString(row.siteId()),
                row.siteName(),
                row.registrationStartsAt().atOffset(ZoneOffset.UTC),
                row.registrationEndsAt().atOffset(ZoneOffset.UTC),
                row.examStartsAt().atOffset(ZoneOffset.UTC),
                row.examEndsAt().atOffset(ZoneOffset.UTC),
                row.capacity(),
                row.registrationCount(),
                row.applicationCode(),
                row.description(),
                row.status(),
                Long.toString(row.projectId()),
                row.registrationOpen(),
                capabilities);
    }

    public static AdminResponses.Audit audit(AdminReadMapper.AuditRow row) {
        return new AdminResponses.Audit(
                row.id(),
                row.occurredAt().atOffset(ZoneOffset.UTC),
                row.operatorName(),
                row.action(),
                row.targetLabel(),
                row.scopeLabel(),
                row.result());
    }

    private static String id(Long value) {
        return value == null ? null : value.toString();
    }

    // endregion
}
