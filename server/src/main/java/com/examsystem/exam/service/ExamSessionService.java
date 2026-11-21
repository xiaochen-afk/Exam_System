package com.examsystem.exam.service;

import com.examsystem.audit.service.AuditService;
import com.examsystem.auth.service.StaffAccessService;
import com.examsystem.common.error.ApiException;
import com.examsystem.exam.dto.ExamRequests;
import com.examsystem.exam.entity.ExamProject;
import com.examsystem.exam.entity.ExamSession;
import com.examsystem.exam.entity.ExamSite;
import com.examsystem.exam.entity.ExamSubject;
import com.examsystem.exam.mapper.ExamProjectMapper;
import com.examsystem.exam.mapper.ExamSessionMapper;
import com.examsystem.exam.mapper.ProjectSiteMapper;
import com.examsystem.exam.validation.ExamRules;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 项目、报名开关与场次命令；账号→科目→项目→考点→项目考点→场次，与报名锁序一致。 */
@Service
public class ExamSessionService {
    private final StaffAccessService access;
    private final ExamLocks locks;
    private final ExamProjectMapper projects;
    private final ExamSessionMapper sessions;
    private final ProjectSiteMapper projectSites;
    private final AuditService audit;

    public ExamSessionService(
            StaffAccessService access,
            ExamLocks locks,
            ExamProjectMapper projects,
            ExamSessionMapper sessions,
            ProjectSiteMapper projectSites,
            AuditService audit) {
        this.access = access;
        this.locks = locks;
        this.projects = projects;
        this.sessions = sessions;
        this.projectSites = projectSites;
        this.audit = audit;
    }

    // region 稳定项目与项目考点开放

    /** 仅创建稳定项目，不隐式自授考务权限；管理员取得 ID 后按项目显式分配动作。 */
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
    public long createProject(ExamRequests.ProjectInput input, String requestId) {
        var actor = access.writer();
        actor.requireCatalog("projects.create");
        var subject = locks.subject(ExamRules.id(input.subjectId()));
        if (!"ACTIVE".equals(subject.getStatus())) throw ExamRules.conflict("科目未启用");
        String code = ExamRules.code(input.applicationCode(), true);
        ExamProject row = new ExamProject();
        row.setSubjectId(subject.getId());
        row.setApplicationCode(code.isEmpty() ? null : code);
        row.setMaxScore(new BigDecimal("100.00"));
        try {
            projects.insert(row);
        } catch (DuplicateKeyException conflict) {
            throw ExamRules.conflict("同科目的报考项目代码已存在，请使用已有项目");
        }
        audit.resourceSuccess(
                actor.id(),
                "projects.create",
                "PROJECT",
                row.getId(),
                "PROJECT#" + row.getId(),
                Map.of("subjectId", subject.getId()),
                requestId);
        return row.getId();
    }

    /** 该开关影响同项目同考点的全部场次；SESSION 发布权限不能替代 PROJECT 开放权限。 */
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
    public void setRegistrationOpen(
            long projectId, long siteId, ExamRequests.ProjectSiteInput input, String requestId) {
        var actor = access.writer();
        actor.requireProject("sessions.publish", projectId);
        if (input.registrationOpen() == null) throw ApiException.invalid("缺少报名开放状态");
        var project = projectBeforeLock(projectId);
        var subject = locks.subject(project.getSubjectId());
        locks.project(projectId);
        var site = locks.site(siteId);
        projectSites.ensure(projectId, siteId);
        projectSites.lockOpen(projectId, siteId);
        if (input.registrationOpen()) requireActive(subject, site);
        projectSites.setOpen(projectId, siteId, input.registrationOpen());
        audit.resourceSuccess(
                actor.id(),
                "project-sites.update",
                "PROJECT_SITE",
                siteId,
                "PROJECT#" + projectId,
                Map.of("registrationOpen", input.registrationOpen()),
                requestId);
    }

    // endregion

    // region 场次创建与维护

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
    public long createSession(ExamRequests.SessionInput input, String requestId) {
        var actor = access.writer();
        long projectId = ExamRules.id(input.projectId());
        actor.requireProject("sessions.write", projectId);
        var before = projectBeforeLock(projectId);
        var subject = locks.subject(before.getSubjectId());
        var project = locks.project(projectId);
        var site = locks.site(ExamRules.id(input.siteId()));
        requireActive(subject, site);
        validateProjectInput(input, project);
        // 空代码项目必须独立，项目锁串行检查，不能让两个并发创建都通过。
        if (project.getApplicationCode() == null
                && sessions.selectCount(
                                com.baomidou.mybatisplus.core.toolkit.Wrappers
                                        .<ExamSession>lambdaQuery()
                                        .eq(ExamSession::getProjectId, projectId))
                        > 0) throw ExamRules.conflict("独立项目仅允许一个场次，跨考点请使用有代码的报考项目");
        if (input.status() != null && !"DRAFT".equals(input.status()))
            throw ApiException.invalid("新场次必须为草稿");
        projectSites.ensure(projectId, site.getId());
        projectSites.lockOpen(projectId, site.getId());
        ExamSession row = new ExamSession();
        row.setProjectId(projectId);
        row.setSiteId(site.getId());
        row.setName(ExamRules.text(input.name(), 100, false));
        row.setDescription(
                ExamRules.text(input.description() == null ? "" : input.description(), 2000, true));
        row.setRegistrationStartsAt(ExamRules.utc(input.registrationStartsAt()));
        row.setRegistrationEndsAt(ExamRules.utc(input.registrationEndsAt()));
        row.setExamStartsAt(ExamRules.utc(input.examStartsAt()));
        row.setExamEndsAt(ExamRules.utc(input.examEndsAt()));
        row.setCapacity(ExamRules.capacity(input.capacity()));
        row.setRegisteredCount(0);
        row.setStatus("DRAFT");
        validateTimes(row);
        sessions.insert(row);
        audit.resourceSuccess(
                actor.id(),
                "sessions.create",
                "SESSION",
                row.getId(),
                "PROJECT#" + projectId,
                Map.of("status", "DRAFT"),
                requestId);
        return row.getId();
    }

    /** 归属固定；已有报名后锁定时间窗口，已发准考证/成绩或结考后拒绝基础修改。 */
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
    public long updateSession(long id, ExamRequests.SessionInput input, String requestId) {
        var actor = access.writer();
        var before = sessions.selectById(id);
        if (before == null) throw ApiException.notFound();
        boolean publish = input.status() != null;
        boolean edits = hasEdits(input);
        if (publish) actor.requireSession("sessions.publish", id, before.getProjectId());
        if (edits || !publish) actor.requireSession("sessions.write", id, before.getProjectId());
        var projectBefore = projectBeforeLock(before.getProjectId());
        var subject = locks.subject(projectBefore.getSubjectId());
        var project = locks.project(before.getProjectId());
        var site = locks.site(before.getSiteId());
        Boolean open = projectSites.lockOpen(project.getId(), site.getId());
        var row = locks.session(id);
        if (!row.getProjectId().equals(before.getProjectId())
                || !row.getSiteId().equals(before.getSiteId()))
            throw ExamRules.conflict("场次归属已变化，请刷新后重试");
        if ("COMPLETED".equals(row.getStatus())
                || row.getTicketPublishedAt() != null
                || row.getScorePublishedAt() != null)
            throw ExamRules.conflict("场次已结考或已开放准考证/成绩，基础信息已锁定");
        if (input.projectId() != null && !input.projectId().equals(row.getProjectId())
                || input.siteId() != null && !input.siteId().equals(row.getSiteId()))
            throw ExamRules.conflict("场次创建后项目和考点固定，请新增草稿场次");
        validateProjectInput(input, project);
        var lockedTimes =
                List.of(
                        row.getRegistrationStartsAt(),
                        row.getRegistrationEndsAt(),
                        row.getExamStartsAt(),
                        row.getExamEndsAt());
        applyChanges(row, input);
        if (row.getRegisteredCount() > 0
                && !lockedTimes.equals(
                        List.of(
                                row.getRegistrationStartsAt(),
                                row.getRegistrationEndsAt(),
                                row.getExamStartsAt(),
                                row.getExamEndsAt())))
            throw ExamRules.conflict("已有报名，暂不支持调整报名或考试时间");
        if (row.getCapacity() < row.getRegisteredCount()) throw ExamRules.conflict("人数上限不能低于已报名人数");
        if (publish) {
            if (!"PUBLISHED".equals(input.status()))
                throw ApiException.invalid("当前仅支持草稿发布，不支持撤回或结考");
            requireActive(subject, site);
            if (!Boolean.TRUE.equals(open)) throw ExamRules.conflict("请先由获项目发布权限的考务开放该项目考点报名");
            row.setStatus("PUBLISHED");
        }
        sessions.updateById(row);
        audit.resourceSuccess(
                actor.id(),
                publish ? "sessions.publish" : "sessions.update",
                "SESSION",
                id,
                "PROJECT#" + project.getId(),
                Map.of("status", row.getStatus()),
                requestId);
        return id;
    }

    // endregion

    // region 共享校验与字段合并

    private ExamProject projectBeforeLock(long id) {
        ExamProject row = projects.selectById(id);
        if (row == null) throw ApiException.notFound();
        return row;
    }

    private static void requireActive(ExamSubject subject, ExamSite site) {
        if (!"ACTIVE".equals(subject.getStatus())
                || !"ACTIVE".equals(site.getStatus())
                || !"QUALIFIED".equals(site.getQualificationStatus()))
            throw ExamRules.conflict("科目、考点须启用且考点具备资格");
    }

    private static void validateProjectInput(ExamRequests.SessionInput input, ExamProject project) {
        if (input.subjectId() != null && !input.subjectId().equals(project.getSubjectId()))
            throw ExamRules.conflict("科目必须与所选报考项目一致");
        if (input.applicationCode() != null
                && !ExamRules.code(input.applicationCode(), true)
                        .equals(Objects.requireNonNullElse(project.getApplicationCode(), "")))
            throw ExamRules.conflict("报考项目代码固定，请选择正确项目");
    }

    private static boolean hasEdits(ExamRequests.SessionInput input) {
        return input.projectId() != null
                || input.subjectId() != null
                || input.siteId() != null
                || input.applicationCode() != null
                || input.name() != null
                || input.description() != null
                || input.capacity() != null
                || input.registrationStartsAt() != null
                || input.registrationEndsAt() != null
                || input.examStartsAt() != null
                || input.examEndsAt() != null;
    }

    private static void applyChanges(ExamSession row, ExamRequests.SessionInput input) {
        if (input.name() != null) row.setName(ExamRules.text(input.name(), 100, false));
        if (input.description() != null)
            row.setDescription(ExamRules.text(input.description(), 2000, true));
        if (input.capacity() != null) row.setCapacity(ExamRules.capacity(input.capacity()));
        if (input.registrationStartsAt() != null)
            row.setRegistrationStartsAt(ExamRules.utc(input.registrationStartsAt()));
        if (input.registrationEndsAt() != null)
            row.setRegistrationEndsAt(ExamRules.utc(input.registrationEndsAt()));
        if (input.examStartsAt() != null) row.setExamStartsAt(ExamRules.utc(input.examStartsAt()));
        if (input.examEndsAt() != null) row.setExamEndsAt(ExamRules.utc(input.examEndsAt()));
        validateTimes(row);
    }

    private static void validateTimes(ExamSession row) {
        ExamRules.times(
                row.getRegistrationStartsAt(),
                row.getRegistrationEndsAt(),
                row.getExamStartsAt(),
                row.getExamEndsAt());
    }

    // endregion
}
