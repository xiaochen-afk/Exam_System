package com.examsystem.auth.model;

import java.io.Serializable;

/** 认证持久化与安全上下文模型；不作为通用 HTTP 响应，敏感模型显式限制 toString。 */
public final class AuthData {
    private AuthData() {}

    // region 账号与凭据模型

    public record User(
            long id, String accountKind, String displayName, String status, long sessionVersion) {}

    public record Credential(long userId, String passwordHash) {
        @Override
        public String toString() {
            return "Credential[userId=" + userId + "]";
        }
    }

    // endregion

    // region 会话身份模型

    /** 会话只保存身份与失效版本，不保存权限快照，撤权必须在后续请求立即生效。 */
    public record Principal(long userId, long sessionVersion) implements Serializable {}

    // endregion

    // region 授权查询模型

    /** 一行表示某条授权内的一个动作，grantId 保留动作和范围的绑定关系。 */
    public record GrantAction(
            long grantId,
            String roleCode,
            String scopeType,
            Long projectId,
            Long sessionId,
            String permissionCode) {}

    public record OwnedGrantAction(
            long staffId,
            long grantId,
            String roleCode,
            String scopeType,
            Long projectId,
            Long sessionId,
            String permissionCode) {
        public GrantAction action() {
            return new GrantAction(
                    grantId, roleCode, scopeType, projectId, sessionId, permissionCode);
        }
    }

    // endregion

}
