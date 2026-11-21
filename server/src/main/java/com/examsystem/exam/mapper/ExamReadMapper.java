package com.examsystem.exam.mapper;

import com.examsystem.exam.dto.ExamResponses;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 必要关联读取共用范围/筛选片段；参数由服务层规范化，选项不携带报名或容量。 */
@Mapper
public interface ExamReadMapper {
    record SiteRow(
            long id,
            String name,
            Long schoolId,
            String schoolName,
            String address,
            String qualificationStatus,
            String status,
            boolean editable,
            boolean manageable) {}

    record RoomRow(
            long id,
            long siteId,
            String siteName,
            String name,
            int capacity,
            String status,
            boolean editable) {}

    record ProjectRow(long id, long subjectId, String subjectName, String applicationCode) {}

    List<SiteRow> sites(
            @Param("actor") long actor,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("size") int size,
            @Param("offset") long offset);

    long siteCount(
            @Param("actor") long actor,
            @Param("keyword") String keyword,
            @Param("status") String status);

    List<RoomRow> rooms(
            @Param("actor") long actor,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("size") int size,
            @Param("offset") long offset);

    long roomCount(
            @Param("actor") long actor,
            @Param("keyword") String keyword,
            @Param("status") String status);

    List<ProjectRow> projects(
            @Param("actor") long actor,
            @Param("keyword") String keyword,
            @Param("size") int size,
            @Param("offset") long offset);

    long projectCount(@Param("actor") long actor, @Param("keyword") String keyword);

    long canSite(
            @Param("actor") long actor, @Param("action") String action, @Param("site") long site);

    List<ExamResponses.Option> projectOptions(@Param("actor") long actor);

    List<ExamResponses.Option> siteOptions(
            @Param("actor") long actor, @Param("context") String context);

    List<ExamResponses.Option> subjectOptions();

    List<ExamResponses.Option> schoolOptions();

    /** 调用方持有考点锁，后续排考写入须遵循相同锁；不返回考生数据。 */
    long siteHasProtectedSessions(@Param("site") long site);

    int roomMaxSeat(@Param("room") long room);

    long roomAssignments(@Param("room") long room);

    long roomHasPublishedTickets(@Param("room") long room);
}
