package com.examsystem.registration.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 报名输入白名单；考生 ID、项目/考点、时间和姓名快照均从服务端读取。 */
public final class RegistrationRequests {
    private RegistrationRequests() {}

    public enum CandidateType {
        STUDENT,
        SOCIAL
    }

    public enum Status {
        REGISTERED
    }

    public record ProfileUpdate(@NotNull CandidateType candidateType, @Positive Long schoolId) {}

    /** 提交仅接受空 JSON 对象，拒绝额外的身份、容量或时间字段。 */
    public record Submission() {}

    public record AdminFilter(
            Long sessionId,
            Long siteId,
            CandidateType candidateType,
            Status status,
            String registrationNumber) {}
}
