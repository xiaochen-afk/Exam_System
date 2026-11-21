package com.examsystem.auth.dto;

import java.util.List;

/** 认证响应 DTO，与业务服务和持久化模型分开。 */
public final class AuthResponses {
    private AuthResponses() {}

    public record Identity(
            String id, String displayName, List<String> roles, List<String> permissions) {}
}
