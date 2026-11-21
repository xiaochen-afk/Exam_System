package com.examsystem.exam.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.examsystem.audit.service.AuditService;
import com.examsystem.auth.service.StaffAccessService;
import com.examsystem.catalog.entity.School;
import com.examsystem.catalog.mapper.SchoolMapper;
import com.examsystem.common.dto.PageResponse;
import com.examsystem.common.query.PageQuery;
import com.examsystem.exam.assembler.ExamResponseAssembler;
import com.examsystem.exam.dto.ExamRequests;
import com.examsystem.exam.dto.ExamResponses;
import com.examsystem.exam.entity.ExamSubject;
import com.examsystem.exam.mapper.ExamSubjectMapper;
import com.examsystem.exam.validation.ExamRules;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 学校/科目基础目录；常规 CRUD 与分页复用 Plus，停用保留关联及历史记录。 */
@Service
public class ExamCatalogService {
    private final SchoolMapper schools;
    private final ExamSubjectMapper subjects;
    private final StaffAccessService access;
    private final ExamLocks locks;
    private final AuditService audit;

    public ExamCatalogService(
            SchoolMapper schools,
            ExamSubjectMapper subjects,
            StaffAccessService access,
            ExamLocks locks,
            AuditService audit) {
        this.schools = schools;
        this.subjects = subjects;
        this.access = access;
        this.locks = locks;
        this.audit = audit;
    }

    // region 目录读取：在同一快照内完成分页条目和计数

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<ExamResponses.School> schools(
            int page, int size, String keyword, String status) {
        var actor = access.reader();
        actor.requireCatalog("sites.read");
        PageQuery query = new PageQuery(page, size, keyword);
        filterStatus(status);
        var where =
                Wrappers.<School>lambdaQuery()
                        .eq(!status.isEmpty(), School::getStatus, status)
                        .apply(
                                "(name LIKE CONCAT('%', {0}, '%') ESCAPE '!' OR CONVERT(code USING utf8mb4) LIKE CONCAT('%', {0}, '%') ESCAPE '!')",
                                query.keyword())
                        .orderByDesc(School::getId);
        IPage<School> result = schools.selectPage(new Page<>(query.page(), query.size()), where);
        return query.response(
                result.getRecords().stream()
                        .map(
                                row ->
                                        ExamResponseAssembler.school(
                                                row, actor.catalog("sites.manage")))
                        .toList(),
                result.getTotal());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<ExamResponses.Subject> subjects(
            int page, int size, String keyword, String status) {
        var actor = access.reader();
        actor.requireCatalog("subjects.read");
        PageQuery query = new PageQuery(page, size, keyword);
        filterStatus(status);
        var where =
                Wrappers.<ExamSubject>lambdaQuery()
                        .eq(!status.isEmpty(), ExamSubject::getStatus, status)
                        .apply(
                                "(name LIKE CONCAT('%', {0}, '%') ESCAPE '!' OR CONVERT(code USING utf8mb4) LIKE CONCAT('%', {0}, '%') ESCAPE '!')",
                                query.keyword())
                        .orderByDesc(ExamSubject::getId);
        IPage<ExamSubject> result =
                subjects.selectPage(new Page<>(query.page(), query.size()), where);
        return query.response(
                result.getRecords().stream()
                        .map(
                                row ->
                                        ExamResponseAssembler.subject(
                                                row, actor.catalog("subjects.write")))
                        .toList(),
                result.getTotal());
    }

    // endregion

    // region 目录写入：操作者锁 → 资源锁 → 当前值校验 → 数据与审计一起提交

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
    public long saveSchool(Long id, ExamRequests.SchoolInput input, String requestId) {
        var actor = access.writer();
        actor.requireCatalog("sites.manage");
        School row = id == null ? new School() : locks.school(ExamRules.id(id));
        row.setCode(
                ExamRules.code(
                        input.code() == null && id != null ? row.getCode() : input.code(), false));
        row.setName(
                ExamRules.text(
                        input.name() == null && id != null ? row.getName() : input.name(),
                        100,
                        false));
        row.setStatus(
                ExamRules.status(
                        input.status() == null
                                ? (id == null ? "ACTIVE" : row.getStatus())
                                : input.status()));
        try {
            if (id == null) schools.insert(row);
            else schools.updateById(row);
        } catch (DuplicateKeyException conflict) {
            throw ExamRules.conflict("学校代码已存在");
        }
        audit.resourceSuccess(
                actor.id(),
                id == null ? "schools.create" : "schools.update",
                "SCHOOL",
                row.getId(),
                "CATALOG",
                Map.of("status", row.getStatus()),
                requestId);
        return row.getId();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
    public long saveSubject(Long id, ExamRequests.SubjectInput input, String requestId) {
        var actor = access.writer();
        actor.requireCatalog("subjects.write");
        ExamSubject row = id == null ? new ExamSubject() : locks.subject(ExamRules.id(id));
        row.setCode(
                ExamRules.code(
                        input.code() == null && id != null ? row.getCode() : input.code(), false));
        row.setName(
                ExamRules.text(
                        input.name() == null && id != null ? row.getName() : input.name(),
                        100,
                        false));
        row.setDescription(
                ExamRules.text(
                        input.description() == null
                                ? (id == null ? "" : row.getDescription())
                                : input.description(),
                        2000,
                        true));
        row.setStatus(
                ExamRules.status(
                        input.status() == null
                                ? (id == null ? "ACTIVE" : row.getStatus())
                                : input.status()));
        try {
            if (id == null) subjects.insert(row);
            else subjects.updateById(row);
        } catch (DuplicateKeyException conflict) {
            throw ExamRules.conflict("科目代码已存在");
        }
        audit.resourceSuccess(
                actor.id(),
                id == null ? "subjects.create" : "subjects.update",
                "SUBJECT",
                row.getId(),
                "CATALOG",
                Map.of("status", row.getStatus()),
                requestId);
        return row.getId();
    }

    // endregion

    static void filterStatus(String status) {
        if (!status.isEmpty()) ExamRules.status(status);
    }
}
