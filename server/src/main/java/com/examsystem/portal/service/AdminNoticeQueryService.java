package com.examsystem.portal.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.examsystem.common.dto.PageResponse;
import com.examsystem.common.error.ApiException;
import com.examsystem.common.query.PageQuery;
import com.examsystem.portal.assembler.AdminNoticeAssembler;
import com.examsystem.portal.dto.AdminNoticeDtos;
import com.examsystem.portal.dto.PortalQueries;
import com.examsystem.portal.entity.Notice;
import com.examsystem.portal.entity.NoticeAttachment;
import com.examsystem.portal.mapper.NoticeAttachmentMapper;
import com.examsystem.portal.mapper.NoticeMapper;
import com.examsystem.portal.model.NoticeStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 管理查询覆盖各状态，并且只在详情中读取正文。 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AdminNoticeQueryService {
    private final NoticeAdminAccess access;
    private final NoticeMapper notices;
    private final NoticeAttachmentMapper attachments;
    private final NoticeAttachmentStorage storage;

    public AdminNoticeQueryService(
            NoticeAdminAccess access,
            NoticeMapper notices,
            NoticeAttachmentMapper attachments,
            NoticeAttachmentStorage storage) {
        this.access = access;
        this.notices = notices;
        this.attachments = attachments;
        this.storage = storage;
    }

    public PageResponse<AdminNoticeDtos.Item> list(
            int page,
            int size,
            String keyword,
            PortalQueries.NoticeCategory category,
            NoticeStatus status) {
        access.requireReader();
        PageQuery paging = new PageQuery(page, size, keyword);
        var query = Wrappers.<Notice>lambdaQuery();
        if (category != null) query.eq(Notice::getCategory, category.name());
        if (status != null) query.eq(Notice::getStatus, status.name());
        if (!paging.keyword().isEmpty()) {
            query.apply("title LIKE {0} ESCAPE '!'", "%" + paging.keyword() + "%");
        }
        query.orderByDesc(Notice::getUpdatedAt, Notice::getId);
        var rows = notices.selectPage(new Page<>(page, size), query);
        return paging.response(
                rows.getRecords().stream().map(AdminNoticeAssembler::item).toList(),
                rows.getTotal());
    }

    public AdminNoticeDtos.Detail detail(long id) {
        access.requireReader();
        Notice row = notices.selectOne(notices.withContent().eq(Notice::getId, id));
        if (row == null) throw ApiException.notFound();
        var files =
                attachments.selectList(
                        Wrappers.<NoticeAttachment>lambdaQuery()
                                .eq(NoticeAttachment::getNoticeId, id)
                                .orderByAsc(NoticeAttachment::getId));
        return new AdminNoticeDtos.Detail(
                AdminNoticeAssembler.item(row),
                row.getBody(),
                files.stream().map(AdminNoticeAssembler::attachment).toList());
    }

    public NoticeAttachmentStorage.Download attachment(long id, long fileId) {
        access.requireReader();
        return storage.load(attachments.requireStoredFile(id, fileId));
    }
}
