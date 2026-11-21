package com.examsystem.auth.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** 后台响应字段白名单；ID 以字符串输出，不携带持久化实体或密码字段。 */
public final class AdminResponses {
    private AdminResponses() {}

    // region 账号与角色目录

    /** 动作和范围始终绑定在同一条授权中，不能拆成两个互相组合的列表。 */
    public record Grant(
            String roleCode,
            String scopeType,
            String projectId,
            String sessionId,
            List<String> permissionCodes) {}

    public record Staff(
            String id,
            String displayName,
            String identifier,
            String status,
            List<Grant> grants,
            String roleLabel,
            String scopeLabel,
            List<String> capabilities) {}

    public record ScopePermission(
            String scopeType, String permissionCode, String permissionLabel) {}

    public record Role(
            String id,
            String name,
            String description,
            List<String> permissions,
            List<String> permissionLabels,
            List<ScopePermission> scopes) {}

    /** 授权表单仅需资源标识与名称，不包含报名、容量、成绩或个人资料。 */
    public record NamedOption(String id, String name) {}

    public record GrantOptions(
            List<Role> roles, List<NamedOption> projects, List<NamedOption> sessions) {}

    // endregion

    // region 工作台与授权场次

    public record Metric(String key, long value) {}

    public record Dashboard(List<Metric> metrics) {}

    /** 仅用于已授权的内部场次列表，不能直接复用为公开或考生场次响应。 */
    public record Session(
            String id,
            String name,
            String subjectId,
            String subjectName,
            String siteId,
            String siteName,
            OffsetDateTime registrationStartsAt,
            OffsetDateTime registrationEndsAt,
            OffsetDateTime examStartsAt,
            OffsetDateTime examEndsAt,
            int capacity,
            int registrationCount,
            String applicationCode,
            String description,
            String status,
            String projectId,
            boolean registrationOpen,
            List<String> capabilities) {}

    // endregion

    /** 不含原始请求、个人姓名或审计变更 JSON。 */
    public record Audit(
            String id,
            OffsetDateTime occurredAt,
            String operatorName,
            String action,
            String targetLabel,
            String scopeLabel,
            String result) {}
}
