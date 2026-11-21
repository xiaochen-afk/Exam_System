package com.examsystem.portal.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 公开场次与考点的关联投影；XML 共用可见性、状态及筛选，不返回内部数量。 */
@Mapper
public interface PublicExamMapper {
    record SessionRow(
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
            String description) {}

    record SiteRow(long id, String name, String address) {}

    /** keyword 已由 PageQuery 转义；now 为本次请求共用的 UTC 时间。 */
    List<SessionRow> sessions(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("now") LocalDateTime now,
            @Param("size") int size,
            @Param("offset") long offset);

    long sessionCount(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("now") LocalDateTime now);

    SessionRow session(@Param("id") long id, @Param("now") LocalDateTime now);

    List<SiteRow> sites(@Param("size") int size, @Param("offset") long offset);

    long siteCount();
}
