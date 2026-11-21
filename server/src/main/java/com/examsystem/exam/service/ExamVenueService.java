package com.examsystem.exam.service;

import com.examsystem.audit.service.AuditService;
import com.examsystem.auth.service.StaffAccessService;
import com.examsystem.common.error.ApiException;
import com.examsystem.exam.dto.ExamRequests;
import com.examsystem.exam.entity.ExamRoom;
import com.examsystem.exam.entity.ExamSite;
import com.examsystem.exam.mapper.ExamReadMapper;
import com.examsystem.exam.mapper.ExamRoomMapper;
import com.examsystem.exam.mapper.ExamSiteMapper;
import com.examsystem.exam.validation.ExamRules;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 场地命令：操作者先锁定，学校/考点/考场按固定顺序锁定，状态变更不删除历史。 */
@Service
public class ExamVenueService {
    private final StaffAccessService access;
    private final ExamLocks locks;
    private final ExamSiteMapper sites;
    private final ExamRoomMapper rooms;
    private final ExamReadMapper reads;
    private final AuditService audit;

    public ExamVenueService(
            StaffAccessService access,
            ExamLocks locks,
            ExamSiteMapper sites,
            ExamRoomMapper rooms,
            ExamReadMapper reads,
            AuditService audit) {
        this.access = access;
        this.locks = locks;
        this.sites = sites;
        this.rooms = rooms;
        this.reads = reads;
        this.audit = audit;
    }

    // region 考点与资格

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
    public long saveSite(Long id, ExamRequests.SiteInput input, String requestId) {
        var actor = access.writer();
        ExamSite before = id == null ? new ExamSite() : sites.selectById(ExamRules.id(id));
        if (before == null) throw ApiException.notFound();
        boolean manager = actor.catalog("sites.manage");
        if (id == null) actor.requireCatalog("sites.manage");
        else if (!manager && reads.canSite(actor.id(), "sites.write", id) == 0)
            throw ApiException.notFound();
        // 普通考点负责人仅能改地址，拒绝夹带其他字段；不能借同名学校建立权限。
        if (!manager
                && (input.name() != null
                        || input.schoolId() != null
                        || input.clearSchool() != null
                        || input.status() != null
                        || input.qualificationStatus() != null)) throw ApiException.forbidden();
        if (Boolean.TRUE.equals(input.clearSchool()) && input.schoolId() != null)
            throw ApiException.invalid("解除学校关联时不能同时指定学校");
        Long schoolId =
                Boolean.TRUE.equals(input.clearSchool())
                        ? null
                        : (input.schoolId() == null
                                ? before.getSchoolId()
                                : ExamRules.id(input.schoolId()));
        // 先锁关联学校，再锁考点；预读只用于定位，锁后重新比对避免关联已变化。
        Stream.of(before.getSchoolId(), schoolId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .forEach(
                        school -> {
                            var row = locks.school(school);
                            if (school.equals(schoolId)
                                    && !"ACTIVE".equals(row.getStatus())
                                    && !school.equals(before.getSchoolId()))
                                throw ExamRules.conflict("承办学校未启用");
                        });
        ExamSite row = id == null ? before : locks.site(id);
        if (!Objects.equals(row.getSchoolId(), before.getSchoolId()))
            throw ExamRules.conflict("考点关联已变更，请刷新后重试");
        if (id != null && !manager && reads.canSite(actor.id(), "sites.write", id) == 0)
            throw ApiException.notFound();
        String name =
                ExamRules.text(
                        input.name() == null && id != null ? row.getName() : input.name(),
                        100,
                        false);
        String address =
                ExamRules.text(
                        input.address() == null && id != null ? row.getAddress() : input.address(),
                        200,
                        false);
        String status =
                ExamRules.status(
                        input.status() == null
                                ? (id == null ? "ACTIVE" : row.getStatus())
                                : input.status());
        String qualification =
                ExamRules.qualification(
                        input.qualificationStatus() == null
                                ? (id == null ? "UNQUALIFIED" : row.getQualificationStatus())
                                : input.qualificationStatus());
        if (id != null
                && reads.siteHasProtectedSessions(id) > 0
                && (!Objects.equals(row.getSchoolId(), schoolId)
                        || !row.getStatus().equals(status)
                        || !row.getQualificationStatus().equals(qualification)
                        || !row.getAddress().equals(address)
                        || !row.getName().equals(name)))
            throw ExamRules.conflict("考点已有报名或已开放准考证/成绩，暂不支持改变场地信息；可按项目暂停新报名");
        row.setName(name);
        row.setAddress(address);
        row.setSchoolId(schoolId);
        row.setStatus(status);
        row.setQualificationStatus(qualification);
        if (id == null) sites.insert(row);
        else {
            // updateById 默认跳过 null，显式 set 保证解除学校关联能够落库。
            sites.update(
                    null,
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.<ExamSite>lambdaUpdate()
                            .eq(ExamSite::getId, id)
                            .set(ExamSite::getName, name)
                            .set(ExamSite::getAddress, address)
                            .set(ExamSite::getSchoolId, schoolId)
                            .set(ExamSite::getStatus, status)
                            .set(ExamSite::getQualificationStatus, qualification));
        }
        audit.resourceSuccess(
                actor.id(),
                id == null ? "sites.create" : "sites.update",
                "SITE",
                row.getId(),
                manager ? "CATALOG" : "SITE#" + row.getId(),
                Map.of("status", status, "qualificationStatus", qualification),
                requestId);
        return row.getId();
    }

    // endregion

    // region 考场与座位约束

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10)
    public long saveRoom(Long id, ExamRequests.RoomInput input, String requestId) {
        var actor = access.writer();
        actor.requireAction("rooms.write");
        ExamRoom before = id == null ? new ExamRoom() : rooms.selectById(ExamRules.id(id));
        if (before == null) throw ApiException.notFound();
        long siteId =
                ExamRules.id(
                        input.siteId() == null && id != null ? before.getSiteId() : input.siteId());
        if (id != null && siteId != before.getSiteId())
            throw ExamRules.conflict("考场所属考点固定，请在目标考点新增考场");
        if (reads.canSite(actor.id(), "rooms.write", siteId) == 0) throw ApiException.notFound();
        var site = locks.site(siteId);
        if (reads.canSite(actor.id(), "rooms.write", siteId) == 0) throw ApiException.notFound();
        ExamRoom row = id == null ? before : locks.room(id);
        if (id != null && row.getSiteId() != siteId) throw ExamRules.conflict("考场关联已变更，请刷新后重试");
        String name =
                ExamRules.text(
                        input.name() == null && id != null ? row.getName() : input.name(),
                        100,
                        false);
        int capacity =
                ExamRules.capacity(
                        input.capacity() == null && id != null
                                ? row.getCapacity()
                                : input.capacity());
        String status =
                ExamRules.status(
                        input.status() == null
                                ? (id == null ? "ACTIVE" : row.getStatus())
                                : input.status());
        if (id == null && !"ACTIVE".equals(site.getStatus())) throw ExamRules.conflict("所属考点未启用");
        if (id != null) {
            if (reads.roomMaxSeat(id) > capacity) throw ExamRules.conflict("容量不能低于已分配座位号");
            if ("INACTIVE".equals(status) && reads.roomAssignments(id) > 0)
                throw ExamRules.conflict("考场已有排考，不能直接停用");
            if (reads.roomHasPublishedTickets(id) > 0
                    && (!name.equals(row.getName())
                            || capacity != row.getCapacity()
                            || !status.equals(row.getStatus())))
                throw ExamRules.conflict("准考证或成绩已开放，考场信息已锁定");
        }
        row.setSiteId(siteId);
        row.setName(name);
        row.setCapacity(capacity);
        row.setStatus(status);
        try {
            if (id == null) rooms.insert(row);
            else rooms.updateById(row);
        } catch (DuplicateKeyException conflict) {
            throw ExamRules.conflict("同一考点下考场名称已存在");
        }
        audit.resourceSuccess(
                actor.id(),
                id == null ? "rooms.create" : "rooms.update",
                "ROOM",
                row.getId(),
                "SITE#" + siteId,
                Map.of("status", status),
                requestId);
        return row.getId();
    }

    // endregion
}
