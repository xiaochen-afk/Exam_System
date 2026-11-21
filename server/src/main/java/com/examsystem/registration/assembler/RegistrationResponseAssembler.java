package com.examsystem.registration.assembler;

import com.examsystem.portal.assembler.PortalResponseAssembler;
import com.examsystem.registration.dto.RegistrationResponses;
import com.examsystem.registration.mapper.RegistrationReadMapper;
import com.examsystem.registration.model.CandidateProfileState;
import java.time.ZoneOffset;
import java.util.Objects;

/** 纯报名视图组装；无 Mapper 调用，不依据学校授予权限，也不输出内部数量或姓名原文。 */
public final class RegistrationResponseAssembler {
    private RegistrationResponseAssembler() {}

    public static RegistrationResponses.Profile profile(CandidateProfileState state) {
        return new RegistrationResponses.Profile(
                state.candidateType(),
                state.schoolId() == null ? null : state.schoolId().toString(),
                state.schoolName());
    }

    public static RegistrationResponses.CandidateItem candidate(
            RegistrationReadMapper.CandidateRow row) {
        return new RegistrationResponses.CandidateItem(
                Long.toString(row.id()),
                row.registrationNumber(),
                Long.toString(row.sessionId()),
                row.sessionName(),
                row.subjectName(),
                row.siteName(),
                row.examStartsAt().atOffset(ZoneOffset.UTC),
                row.submittedAt().atOffset(ZoneOffset.UTC),
                row.status());
    }

    public static RegistrationResponses.Option option(
            RegistrationReadMapper.OptionRow row,
            CandidateProfileState profile,
            RegistrationResponses.CandidateItem existing) {
        var session = PortalResponseAssembler.session(row.publicSession());
        boolean sameSession = existing != null && existing.sessionId().equals(session.id());
        String reason =
                unavailableReason(
                        session.registrationStatus(),
                        profile.complete(),
                        existing != null,
                        sameSession);
        boolean selectable = reason == null;
        boolean recommended =
                selectable
                        && "STUDENT".equals(profile.candidateType())
                        && profile.schoolId() != null
                        && Objects.equals(profile.schoolId(), row.schoolId());
        return new RegistrationResponses.Option(
                session.id(),
                session.name(),
                session.subjectName(),
                session.siteName(),
                session.siteAddress(),
                session.registrationStatus(),
                session.registrationStartsAt(),
                session.registrationEndsAt(),
                session.examStartsAt(),
                session.examEndsAt(),
                session.description(),
                selectable,
                recommended,
                sameSession ? existing : null,
                reason);
    }

    public static RegistrationResponses.AdminItem admin(RegistrationReadMapper.AdminRow row) {
        return new RegistrationResponses.AdminItem(
                Long.toString(row.id()),
                row.registrationNumber(),
                row.candidateName(),
                row.candidateType(),
                row.schoolName(),
                Long.toString(row.sessionId()),
                row.sessionName(),
                row.subjectName(),
                Long.toString(row.siteId()),
                row.siteName(),
                row.submittedAt().atOffset(ZoneOffset.UTC),
                row.status());
    }

    private static String unavailableReason(
            String status, boolean complete, boolean registered, boolean sameSession) {
        if (registered) return sameSession ? "已报名该场次" : "该报考项目已有报名记录";
        if (!complete) return "请先完善有效的考生类型及学校资料";
        return switch (status) {
            case "OPEN" -> null;
            case "UPCOMING" -> "报名尚未开始";
            case "FULL" -> "该场次名额已满";
            case "CLOSED" -> "报名已经截止";
            default -> "该场次当前暂停报名";
        };
    }
}
