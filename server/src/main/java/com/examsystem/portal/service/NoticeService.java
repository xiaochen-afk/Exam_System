package com.examsystem.portal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.examsystem.common.dto.PageResponse;
import com.examsystem.common.error.ApiException;
import com.examsystem.common.query.PageQuery;
import com.examsystem.portal.assembler.PortalResponseAssembler;
import com.examsystem.portal.dto.PortalQueries;
import com.examsystem.portal.dto.PortalResponses;
import com.examsystem.portal.entity.Notice;
import com.examsystem.portal.entity.NoticeAttachment;
import com.examsystem.portal.mapper.NoticeAttachmentMapper;
import com.examsystem.portal.mapper.NoticeMapper;
import com.examsystem.portal.model.NoticeStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 已发布资讯读取；匿名和登录用户使用相同可见条件，不提供供稿或发布命令。 */
@Service
public class NoticeService {
    private static final String ATTACHMENTS_EXIST =
            "SELECT 1 FROM notice_attachment a WHERE a.notice_id = notice.id";
    private final NoticeMapper notices;
    private final NoticeAttachmentMapper attachments;
    private final NoticeAttachmentStorage storage;
    private final Clock clock;

    public NoticeService(
            NoticeMapper notices,
            NoticeAttachmentMapper attachments,
            NoticeAttachmentStorage storage,
            Clock clock) {
        this.notices = notices;
        this.attachments = attachments;
        this.storage = storage;
        this.clock = clock;
    }

    /** 列表与总数由同一 Wrapper 分页；LIKE 使用绑定参数，%/_ 按字面查询。 */
    @Transactional(readOnly = true)
    public PageResponse<PortalResponses.NoticeItem> list(PortalQueries.Notices filter) {
        PageQuery page = new PageQuery(filter.page(), filter.size(), filter.keyword());
        var query =
                published()
                        .eq(
                                filter.category() != null,
                                Notice::getCategory,
                                filter.category() == null ? null : filter.category().name())
                        .eq(filter.featured() != null, Notice::getFeatured, filter.featured());
        if (!page.keyword().isEmpty()) {
            query.apply("title LIKE {0} ESCAPE '!'", "%" + page.keyword() + "%");
        }
        if (filter.hasAttachments() != null) {
            if (filter.hasAttachments()) query.exists(ATTACHMENTS_EXIST);
            else query.notExists(ATTACHMENTS_EXIST);
        }
        query.orderByDesc(Notice::getPinned, Notice::getPublishedAt, Notice::getId);
        var result = notices.selectPage(new Page<>(page.page(), page.size()), query);
        return page.response(
                result.getRecords().stream().map(PortalResponseAssembler::notice).toList(),
                result.getTotal());
    }

    /** 详情与附件元数据在同一只读事务中读取；草稿、未来发布时间及不存在项统一为 404。 */
    @Transactional(readOnly = true)
    public PortalResponses.NoticeDetail detail(long id) {
        Notice notice = requirePublished(id, true);
        var files =
                attachments.selectList(
                        Wrappers.<NoticeAttachment>lambdaQuery()
                                .select(NoticeAttachment::getId, NoticeAttachment::getFileName)
                                .eq(NoticeAttachment::getNoticeId, id)
                                .orderByAsc(NoticeAttachment::getId));
        return PortalResponseAssembler.noticeDetail(notice, files);
    }

    /** 附件 ID 不能跨资讯复用；先验证父资讯公开，再读取受控目录中的文件。 */
    @Transactional(readOnly = true)
    public NoticeAttachmentStorage.Download attachment(long noticeId, long fileId) {
        requirePublished(noticeId, false);
        return storage.load(attachments.requireStoredFile(noticeId, fileId));
    }

    private Notice requirePublished(long id, boolean includeBody) {
        var query = published().eq(Notice::getId, id);
        if (includeBody) {
            query.select(
                    Notice::getId,
                    Notice::getTitle,
                    Notice::getCategory,
                    Notice::getSummary,
                    Notice::getPinned,
                    Notice::getPublishedAt,
                    Notice::getPublisherLabel,
                    Notice::getBody);
        } else query.select(Notice::getId);
        Notice notice = notices.selectOne(query);
        if (notice == null) throw ApiException.notFound();
        return notice;
    }

    /** 所有公开入口共用发布条件，防止详情或附件绕过列表的发布时间限制。 */
    private LambdaQueryWrapper<Notice> published() {
        return Wrappers.<Notice>lambdaQuery()
                .eq(Notice::getStatus, NoticeStatus.PUBLISHED.name())
                .isNotNull(Notice::getPublishedAt)
                .le(
                        Notice::getPublishedAt,
                        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }
}
