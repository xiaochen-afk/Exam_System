package com.examsystem.registration.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** 报名 HTTP 响应白名单；本人/选项无名额数量，后台姓名脱敏，不返回凭据或联系方式。 */
public final class RegistrationResponses {
    private RegistrationResponses() {}

    public record Profile(String candidateType, String schoolId, String schoolName) {}

    public record NamedOption(String id, String name) {}

    public record CandidateItem(
            String id,
            String registrationNumber,
            String sessionId,
            String sessionName,
            String subjectName,
            String siteName,
            OffsetDateTime examStartsAt,
            OffsetDateTime submittedAt,
            String status) {}

    public record Option(
            String id,
            String name,
            String subjectName,
            String siteName,
            String siteAddress,
            String registrationStatus,
            OffsetDateTime registrationStartsAt,
            OffsetDateTime registrationEndsAt,
            OffsetDateTime examStartsAt,
            OffsetDateTime examEndsAt,
            String description,
            boolean selectable,
            boolean recommended,
            CandidateItem registration,
            String unavailableReason) {}

    public record Options(List<Option> items) {}

    public record AdminItem(
            String id,
            String registrationNumber,
            String candidateName,
            String candidateType,
            String schoolName,
            String sessionId,
            String sessionName,
            String subjectName,
            String siteId,
            String siteName,
            OffsetDateTime submittedAt,
            String status) {}

    public record Summary(long total, long student, long social, long assigned) {}

    public record AdminPage(
            List<AdminItem> items, int page, int size, long total, Summary summary) {}
}
