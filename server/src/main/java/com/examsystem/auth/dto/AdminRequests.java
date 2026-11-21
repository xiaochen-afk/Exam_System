package com.examsystem.auth.dto;

import java.util.List;

/** 内部账号与授权请求 DTO，与业务服务和持久化模型分开。 */
public final class AdminRequests {
    private AdminRequests() {}

    // region 授权条目

    /** 单条角色/范围/动作绑定；项目或场次 ID 必须按 scopeType 二选一。 */
    public record GrantRequest(
            String roleCode,
            String scopeType,
            Long projectId,
            Long sessionId,
            List<String> permissionCodes) {}

    // endregion

    // region 账号创建请求

    public record StaffRequest(
            String identifier,
            String initialPassword,
            String displayName,
            String status,
            List<GrantRequest> grants) {
        @Override
        public String toString() {
            return "StaffRequest[redacted]";
        }
    }

    // endregion

    // region 账号修改请求

    /** 缺省字段沿用现值；grants 为 null 不修改，为空数组撤销全部。 */
    public record StaffUpdate(String displayName, String status, List<GrantRequest> grants) {}

    // endregion

}
