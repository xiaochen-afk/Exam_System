package com.examsystem.exam.dto;

import java.util.List;

/** 管理端字段白名单；实体、授权明细和内部统计不进入基础选项。 */
public final class ExamResponses {
    private ExamResponses() {}

    public record Option(String id, String name) {}

    public record School(
            String id, String code, String name, String status, List<String> capabilities) {}

    public record Subject(
            String id,
            String code,
            String name,
            String description,
            String status,
            List<String> capabilities) {}

    public record Site(
            String id,
            String name,
            String schoolId,
            String schoolName,
            String address,
            String qualificationStatus,
            String status,
            List<String> capabilities) {}

    public record Room(
            String id,
            String siteId,
            String siteName,
            String name,
            int capacity,
            String status,
            List<String> capabilities) {}

    public record Project(
            String id,
            String subjectId,
            String subjectName,
            String applicationCode,
            List<String> capabilities) {}
}
