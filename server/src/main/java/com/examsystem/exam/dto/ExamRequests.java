package com.examsystem.exam.dto;

import java.time.OffsetDateTime;

/** 基础维护输入白名单；PATCH 缺省字段保留原值，归属 ID 不由角色标签推导。 */
public final class ExamRequests {
    private ExamRequests() {}

    public record SchoolInput(String code, String name, String status) {}

    public record SubjectInput(String code, String name, String description, String status) {}

    /** 学校使用稳定 ID；clearSchool 显式解除关联，不能按学校显示名自动建目录。 */
    public record SiteInput(
            String name,
            Long schoolId,
            Boolean clearSchool,
            String address,
            String qualificationStatus,
            String status) {}

    public record RoomInput(Long siteId, String name, Integer capacity, String status) {}

    /** 项目建立后由管理员授权；创建动作不等价于该项目的场次或报名操作权。 */
    public record ProjectInput(Long subjectId, String applicationCode) {}

    public record ProjectSiteInput(Boolean registrationOpen) {}

    /** 时间须带偏移，持久化时统一转换 UTC；项目与考点创建后固定，避免授权漂移。 */
    public record SessionInput(
            Long projectId,
            Long subjectId,
            Long siteId,
            String applicationCode,
            String name,
            String description,
            OffsetDateTime registrationStartsAt,
            OffsetDateTime registrationEndsAt,
            OffsetDateTime examStartsAt,
            OffsetDateTime examEndsAt,
            Integer capacity,
            String status) {}
}
