package com.examsystem.portal.dto;

import com.examsystem.portal.model.NoticeStatus;
import java.time.OffsetDateTime;
import java.util.List;

/** 管理端资讯契约。附件响应只返回显示名、大小和业务 ID。 */
public final class AdminNoticeDtos {
    private AdminNoticeDtos() {}

    public record Content(
            PortalQueries.NoticeCategory category,
            String title,
            String summary,
            String body,
            String publisher,
            Boolean pinned,
            Boolean featured) {}

    public record Publication(Boolean published) {}

    public record Item(
            String id,
            String category,
            String title,
            String summary,
            String publisher,
            boolean pinned,
            boolean featured,
            NoticeStatus status,
            OffsetDateTime publishedAt,
            OffsetDateTime updatedAt,
            OffsetDateTime withdrawnAt) {}

    public record Detail(Item notice, String body, List<Attachment> attachments) {}

    public record Attachment(String id, String filename, long byteCount) {}
}
