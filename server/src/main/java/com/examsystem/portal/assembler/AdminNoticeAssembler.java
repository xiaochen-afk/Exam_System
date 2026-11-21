package com.examsystem.portal.assembler;

import com.examsystem.portal.dto.AdminNoticeDtos;
import com.examsystem.portal.entity.Notice;
import com.examsystem.portal.entity.NoticeAttachment;
import com.examsystem.portal.model.NoticeStatus;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 资讯管理响应的白名单映射。 */
public final class AdminNoticeAssembler {
    private AdminNoticeAssembler() {}

    public static AdminNoticeDtos.Item item(Notice row) {
        return new AdminNoticeDtos.Item(
                row.getId().toString(),
                row.getCategory(),
                row.getTitle(),
                row.getSummary(),
                row.getPublisherLabel(),
                Boolean.TRUE.equals(row.getPinned()),
                Boolean.TRUE.equals(row.getFeatured()),
                NoticeStatus.valueOf(row.getStatus()),
                utc(row.getPublishedAt()),
                utc(row.getUpdatedAt()),
                utc(row.getWithdrawnAt()));
    }

    public static AdminNoticeDtos.Attachment attachment(NoticeAttachment row) {
        return new AdminNoticeDtos.Attachment(
                row.getId().toString(),
                PortalResponseAssembler.safeFilename(row.getFileName()),
                row.getByteCount(),
                utc(row.getWithdrawnAt()));
    }

    private static OffsetDateTime utc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
