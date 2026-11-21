package com.examsystem.registration.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 报名事务专用锁及条件更新；固定账号/资料/学校之后的资源锁顺序，不提供任意表名操作。 */
@Mapper
public interface EnrollmentMapper {
    record Binding(long projectId, long subjectId, long siteId) {}

    record Project(long id, long subjectId) {}

    record Site(long id, String status, String qualificationStatus) {}

    record Session(
            long id,
            long projectId,
            long siteId,
            LocalDateTime registrationStartsAt,
            LocalDateTime registrationEndsAt,
            int capacity,
            int registeredCount,
            String status,
            LocalDateTime ticketPublishedAt,
            LocalDateTime scorePublishedAt) {}

    Binding binding(@Param("id") long id);

    String lockSubject(@Param("id") long id);

    Project lockProject(@Param("id") long id);

    Site lockSite(@Param("id") long id);

    Integer lockProjectSite(@Param("project") long project, @Param("site") long site);

    Session lockSession(@Param("id") long id);

    /** 必须在所有资源锁取得后单独调用，避免等待锁期间跨过截止边界仍沿用旧时间。 */
    LocalDateTime databaseTime();

    int reserve(@Param("id") long id, @Param("now") LocalDateTime now);
}
