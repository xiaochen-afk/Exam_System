package com.examsystem.portal.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.examsystem.audit.service.AuditService;
import com.examsystem.common.error.ApiException;
import com.examsystem.portal.assembler.AdminNoticeAssembler;
import com.examsystem.portal.dto.AdminNoticeDtos;
import com.examsystem.portal.entity.Notice;
import com.examsystem.portal.mapper.NoticeAttachmentMapper;
import com.examsystem.portal.mapper.NoticeMapper;
import com.examsystem.portal.model.NoticeStatus;
import com.examsystem.portal.validation.NoticeContentRules;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 先锁操作者并重新判权，再锁资讯。发布、编辑和附件变更共享同一父记录锁。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED, timeout = 15)
public class AdminNoticeCommandService {
    private final NoticeAdminAccess access;
    private final NoticeMapper notices;
    private final NoticeAttachmentMapper attachments;
    private final NoticeAttachmentStorage storage;
    private final AuditService audit;
    private final Clock clock;

    public AdminNoticeCommandService(
            NoticeAdminAccess access,
            NoticeMapper notices,
            NoticeAttachmentMapper attachments,
            NoticeAttachmentStorage storage,
            AuditService audit,
            Clock clock) {
        this.access = access;
        this.notices = notices;
        this.attachments = attachments;
        this.storage = storage;
        this.audit = audit;
        this.clock = clock;
    }

    // region 内容与发布

    public AdminNoticeDtos.Item create(AdminNoticeDtos.Content changes, String requestId) {
        long actor = access.requireWriter(NoticeAdminAccess.WRITE);
        Notice row = new Notice();
        row.setStatus(NoticeStatus.DRAFT.name());
        row.setPinned(false);
        row.setFeatured(false);
        row.setCreatedAt(now());
        row.setUpdatedAt(row.getCreatedAt());
        NoticeContentRules.apply(row, changes);
        notices.insert(row);
        record(actor, "notices.create", row, Map.of("status", row.getStatus()), requestId);
        return AdminNoticeAssembler.item(row);
    }

    public AdminNoticeDtos.Item update(long id, AdminNoticeDtos.Content changes, String requestId) {
        long actor = access.requireWriter(NoticeAdminAccess.WRITE);
        Notice row = lockNotice(id, true);
        requireEditable(row);
        NoticeContentRules.apply(row, changes);
        row.setUpdatedAt(now());
        notices.updateById(row);
        record(actor, "notices.update", row, Map.of("status", row.getStatus()), requestId);
        return AdminNoticeAssembler.item(row);
    }

    public AdminNoticeDtos.Item setPublication(long id, boolean publish, String requestId) {
        long actor = access.requireWriter(NoticeAdminAccess.PUBLISH);
        Notice row = lockNotice(id, publish);
        NoticeStatus current = NoticeStatus.valueOf(row.getStatus());
        NoticeStatus target = publish ? NoticeStatus.PUBLISHED : NoticeStatus.WITHDRAWN;
        // 重试保留原时间和审计，已排期的历史资讯也不会因重复发布而提前公开。
        if (current == target) return AdminNoticeAssembler.item(row);
        if (!publish && current != NoticeStatus.PUBLISHED) {
            throw conflict("只有已发布的资讯可以下架");
        }
        LocalDateTime changedAt = now();
        var update =
                Wrappers.<Notice>lambdaUpdate()
                        .eq(Notice::getId, id)
                        .set(Notice::getStatus, target.name())
                        .set(Notice::getUpdatedAt, changedAt);
        if (publish) {
            NoticeContentRules.validate(row);
            update.set(Notice::getPublishedAt, changedAt).set(Notice::getWithdrawnAt, null);
            row.setPublishedAt(changedAt);
            row.setWithdrawnAt(null);
        } else {
            update.set(Notice::getWithdrawnAt, changedAt);
            row.setWithdrawnAt(changedAt);
        }
        notices.update(update);
        row.setStatus(target.name());
        row.setUpdatedAt(changedAt);
        record(
                actor,
                publish ? "notices.publish" : "notices.withdraw",
                row,
                Map.of("before", current.name(), "after", target.name()),
                requestId);
        return AdminNoticeAssembler.item(row);
    }

    // endregion

    // region 附件变更

    public AdminNoticeDtos.Attachment upload(long id, MultipartFile file, String requestId) {
        long actor = access.requireWriter(NoticeAdminAccess.ATTACHMENTS);
        Notice row = lockNotice(id, false);
        requireEditable(row);
        var attachment = storage.store(id, file);
        attachments.insert(attachment);
        touch(id);
        record(
                actor,
                "notices.attachments.upload",
                row,
                Map.of("attachmentId", attachment.getId(), "byteCount", attachment.getByteCount()),
                requestId);
        return AdminNoticeAssembler.attachment(attachment);
    }

    public void remove(long id, long fileId, String requestId) {
        long actor = access.requireWriter(NoticeAdminAccess.ATTACHMENTS);
        Notice row = lockNotice(id, false);
        requireEditable(row);
        var attachment = attachments.requireStoredFile(id, fileId);
        attachments.deleteById(fileId);
        storage.removeAfterCommit(attachment);
        touch(id);
        record(actor, "notices.attachments.remove", row, Map.of("attachmentId", fileId), requestId);
    }

    // endregion

    private Notice lockNotice(long id, boolean includeContent) {
        var query = includeContent ? notices.withContent() : Wrappers.<Notice>lambdaQuery();
        Notice row = notices.selectOne(query.eq(Notice::getId, id).last("FOR UPDATE"));
        if (row == null) throw ApiException.notFound();
        return row;
    }

    private static void requireEditable(Notice row) {
        if (NoticeStatus.PUBLISHED.name().equals(row.getStatus())) {
            throw conflict("请先下架资讯，再修改正文或附件");
        }
    }

    private void touch(long id) {
        notices.update(
                Wrappers.<Notice>lambdaUpdate()
                        .eq(Notice::getId, id)
                        .set(Notice::getUpdatedAt, now()));
    }

    private void record(
            long actor, String action, Notice row, Map<String, Object> changes, String requestId) {
        audit.resourceSuccess(actor, action, "NOTICE", row.getId(), "CATALOG", changes, requestId);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
                .truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "NOTICE_STATE_CONFLICT", message);
    }
}
