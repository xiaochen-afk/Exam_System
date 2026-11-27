package com.examsystem.auth.dto;

import java.time.OffsetDateTime;

/** 小程序认证契约；含凭据的传输对象提供脱敏的字符串表示。 */
public final class MiniProgramDtos {
    private MiniProgramDtos() {}

    public record Login(String code) {
        @Override
        public String toString() {
            return "Login[redacted]";
        }
    }

    public record Registration(String code, String displayName) {
        @Override
        public String toString() {
            return "Registration[redacted]";
        }
    }

    public record Link(String code, String identifier, String password) {
        @Override
        public String toString() {
            return "Link[redacted]";
        }
    }

    public record WebCredential(String identifier, String password) {
        @Override
        public String toString() {
            return "WebCredential[redacted]";
        }
    }

    public record Candidate(String id, String displayName, boolean hasWebCredential) {}

    public record Session(String token, OffsetDateTime expiresAt, Candidate candidate) {
        @Override
        public String toString() {
            return "Session[redacted]";
        }
    }
}
