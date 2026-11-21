package com.examsystem.auth.dto;

/** 认证请求 DTO，与业务服务和持久化模型分开。 */
public final class AuthRequests {
    private AuthRequests() {}

    // region 考生注册请求

    public record Registration(String identifier, String password, String displayName) {
        @Override
        public String toString() {
            return "Registration[redacted]";
        }
    }

    // endregion

    // region Web 登录请求

    public record Login(String identifier, String password, String portal) {
        @Override
        public String toString() {
            return "Login[redacted]";
        }
    }

    // endregion

}
