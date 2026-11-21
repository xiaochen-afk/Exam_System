package com.examsystem.exam.assembler;

import com.examsystem.catalog.entity.School;
import com.examsystem.exam.dto.ExamResponses;
import com.examsystem.exam.entity.ExamSubject;
import com.examsystem.exam.mapper.ExamReadMapper;
import java.util.ArrayList;
import java.util.List;

/** 纯字段映射；字符串 ID 和按钮能力集中处理，不在逐行组装时查询数据库。 */
public final class ExamResponseAssembler {
    private ExamResponseAssembler() {}

    private static List<String> edit(boolean editable) {
        return editable ? List.of("EDIT") : List.of();
    }

    public static ExamResponses.School school(School row, boolean editable) {
        return new ExamResponses.School(
                row.getId().toString(),
                row.getCode(),
                row.getName(),
                row.getStatus(),
                edit(editable));
    }

    public static ExamResponses.Subject subject(ExamSubject row, boolean editable) {
        return new ExamResponses.Subject(
                row.getId().toString(),
                row.getCode(),
                row.getName(),
                row.getDescription(),
                row.getStatus(),
                edit(editable));
    }

    public static ExamResponses.Site site(ExamReadMapper.SiteRow row) {
        List<String> capabilities = new ArrayList<>();
        if (row.editable() || row.manageable()) capabilities.add("EDIT");
        if (row.manageable()) capabilities.add("MANAGE");
        return new ExamResponses.Site(
                Long.toString(row.id()),
                row.name(),
                row.schoolId() == null ? null : row.schoolId().toString(),
                row.schoolName(),
                row.address(),
                row.qualificationStatus(),
                row.status(),
                capabilities);
    }

    public static ExamResponses.Room room(ExamReadMapper.RoomRow row) {
        return new ExamResponses.Room(
                Long.toString(row.id()),
                Long.toString(row.siteId()),
                row.siteName(),
                row.name(),
                row.capacity(),
                row.status(),
                edit(row.editable()));
    }

    public static ExamResponses.Project project(ExamReadMapper.ProjectRow row) {
        return new ExamResponses.Project(
                Long.toString(row.id()),
                Long.toString(row.subjectId()),
                row.subjectName(),
                row.applicationCode(),
                List.of());
    }
}
