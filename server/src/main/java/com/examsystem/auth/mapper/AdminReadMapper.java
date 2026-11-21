package com.examsystem.auth.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 已授权后台读取的数据库投影；SQL 在聚合/分页前应用同一授权谓词。 */
@Mapper
public interface AdminReadMapper {
    // region 查询结果类型

    /** 内部场次读取包含容量字段，不能直接用于公开或考生响应。 */
    record SessionRow(
            long id,
            String name,
            long subjectId,
            String subjectName,
            long siteId,
            String siteName,
            LocalDateTime registrationStartsAt,
            LocalDateTime registrationEndsAt,
            LocalDateTime examStartsAt,
            LocalDateTime examEndsAt,
            int capacity,
            int registrationCount,
            String applicationCode,
            String description,
            String status,
            long projectId,
            boolean registrationOpen,
            boolean locked) {}

    /** 审计白名单投影，不选择 summary 等可能包含业务明细的字段。 */
    record AuditRow(
            String id,
            LocalDateTime occurredAt,
            String operatorName,
            String action,
            String targetLabel,
            String scopeLabel,
            String result) {}

    // endregion

    // region 场次列表与授权统计

    /** actor 从服务端身份读取，keyword 已经 LIKE 转义。 */
    List<SessionRow> sessions(
            @Param("actor") long actor,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("size") int size,
            @Param("offset") long offset);

    /** 与 sessions 共用筛选和授权条件，不能泄露范围外总数。 */
    long sessionCount(
            @Param("actor") long actor,
            @Param("keyword") String keyword,
            @Param("status") String status);

    /** assigned=true 只计有排考的报名；permission 由业务代码指定，仍按逐条范围筛选。 */
    long businessCount(
            @Param("actor") long actor,
            @Param("permission") String permission,
            @Param("assigned") boolean assigned);

    // endregion

    // region 审计列表与计数

    List<AuditRow> audit(
            @Param("keyword") String keyword,
            @Param("size") int size,
            @Param("offset") long offset);

    long auditCount(@Param("keyword") String keyword);
    // endregion

}
