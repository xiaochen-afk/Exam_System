package com.examsystem.registration.mapper;

import com.examsystem.portal.mapper.PublicExamMapper;
import com.examsystem.registration.dto.RegistrationResponses;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 报名读取投影；本人按 candidate 限定，后台条目/统计/选项共用实际归属的授权谓词。 */
@Mapper
public interface RegistrationReadMapper {
    record CandidateRow(
            long id,
            String registrationNumber,
            long sessionId,
            String sessionName,
            String subjectName,
            String siteName,
            LocalDateTime examStartsAt,
            LocalDateTime submittedAt,
            String status) {}

    /** schoolId 只用于推荐，不会复制到公开场次或选项响应中。 */
    record OptionRow(
            long id,
            String name,
            String subjectName,
            String siteName,
            String siteAddress,
            String registrationStatus,
            LocalDateTime registrationStartsAt,
            LocalDateTime registrationEndsAt,
            LocalDateTime examStartsAt,
            LocalDateTime examEndsAt,
            String description,
            Long schoolId) {
        public PublicExamMapper.SessionRow publicSession() {
            return new PublicExamMapper.SessionRow(
                    id,
                    name,
                    subjectName,
                    siteName,
                    siteAddress,
                    registrationStatus,
                    registrationStartsAt,
                    registrationEndsAt,
                    examStartsAt,
                    examEndsAt,
                    description);
        }
    }

    /** candidateName 已在 SQL 中脱敏，Mapper 结果不携带完整考生姓名。 */
    record AdminRow(
            long id,
            String registrationNumber,
            String candidateName,
            String candidateType,
            String schoolName,
            long sessionId,
            String sessionName,
            String subjectName,
            long siteId,
            String siteName,
            LocalDateTime submittedAt,
            String status) {}

    record AdminQuery(
            Long sessionId, Long siteId, String candidateType, String status, String keyword) {}

    Long publicProject(@Param("id") long id);

    List<OptionRow> options(@Param("project") long project, @Param("now") LocalDateTime now);

    CandidateRow ownProject(@Param("candidate") long candidate, @Param("project") long project);

    CandidateRow ownDetail(@Param("candidate") long candidate, @Param("id") long id);

    List<CandidateRow> ownList(
            @Param("candidate") long candidate,
            @Param("size") int size,
            @Param("offset") long offset);

    List<AdminRow> adminList(
            @Param("actor") long actor,
            @Param("filter") AdminQuery filter,
            @Param("size") int size,
            @Param("offset") long offset);

    RegistrationResponses.Summary summary(
            @Param("actor") long actor, @Param("filter") AdminQuery filter);

    AdminRow adminDetail(@Param("actor") long actor, @Param("id") long id);

    List<RegistrationResponses.NamedOption> sessionOptions(@Param("actor") long actor);

    List<RegistrationResponses.NamedOption> siteOptions(@Param("actor") long actor);
}
