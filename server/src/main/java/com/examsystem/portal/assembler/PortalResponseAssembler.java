package com.examsystem.portal.assembler;

import com.examsystem.portal.dto.PortalResponses;
import com.examsystem.portal.entity.Notice;
import com.examsystem.portal.entity.NoticeAttachment;
import com.examsystem.portal.mapper.PublicExamMapper;
import java.time.ZoneOffset;
import java.util.List;

/** 纯字段映射；所有时间按数据库 UTC 表达，公开响应与内部考务响应分别维护。 */
public final class PortalResponseAssembler {
    private PortalResponseAssembler() {}

    public static PortalResponses.NoticeItem notice(Notice row) {
        return new PortalResponses.NoticeItem(
                row.getId().toString(),
                row.getTitle(),
                row.getCategory(),
                row.getSummary(),
                Boolean.TRUE.equals(row.getPinned()),
                row.getPublishedAt().atOffset(ZoneOffset.UTC));
    }

    public static PortalResponses.NoticeDetail noticeDetail(
            Notice row, List<NoticeAttachment> attachments) {
        var item = notice(row);
        return new PortalResponses.NoticeDetail(
                item.id(),
                item.title(),
                item.category(),
                item.summary(),
                item.pinned(),
                item.publishedAt(),
                row.getPublisherLabel(),
                row.getBody(),
                attachments.stream()
                        .map(
                                file ->
                                        new PortalResponses.Attachment(
                                                file.getId().toString(),
                                                safeFilename(file.getFileName())))
                        .toList());
    }

    public static PortalResponses.Session session(PublicExamMapper.SessionRow row) {
        return new PortalResponses.Session(
                Long.toString(row.id()),
                row.name(),
                row.subjectName(),
                row.siteName(),
                row.siteAddress(),
                row.registrationStatus(),
                row.registrationStartsAt().atOffset(ZoneOffset.UTC),
                row.registrationEndsAt().atOffset(ZoneOffset.UTC),
                row.examStartsAt().atOffset(ZoneOffset.UTC),
                row.examEndsAt().atOffset(ZoneOffset.UTC),
                row.description());
    }

    public static PortalResponses.Site site(PublicExamMapper.SiteRow row) {
        return new PortalResponses.Site(Long.toString(row.id()), row.name(), row.address());
    }

    /** 下载文件名只作展示，去掉控制字符和路径分隔符，不把它当成内部文件路径。 */
    public static String safeFilename(String filename) {
        String safe = filename.replaceAll("[\\p{Cntrl}\\\\/]", "_");
        return safe.isBlank() || safe.equals(".") || safe.equals("..") ? "attachment" : safe;
    }
}
