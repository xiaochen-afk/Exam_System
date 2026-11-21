package com.examsystem.portal.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** 匿名门户字段白名单；不返回存储键、个人资料、内部容量、报名计数或权限信息。 */
public final class PortalResponses {
    private PortalResponses() {}

    public record NoticeItem(
            String id,
            String title,
            String category,
            String summary,
            boolean pinned,
            OffsetDateTime publishedAt) {}

    public record Attachment(String id, String filename) {}

    /** 正文为纯文本，由客户端按文本渲染，不提供 HTML 或任意外部附件地址。 */
    public record NoticeDetail(
            String id,
            String title,
            String category,
            String summary,
            boolean pinned,
            OffsetDateTime publishedAt,
            String publisher,
            String body,
            List<Attachment> attachments) {}

    public record Session(
            String id,
            String name,
            String subjectName,
            String siteName,
            String siteAddress,
            String registrationStatus,
            OffsetDateTime registrationStartsAt,
            OffsetDateTime registrationEndsAt,
            OffsetDateTime examStartsAt,
            OffsetDateTime examEndsAt,
            String description) {}

    public record Site(String id, String name, String address) {}
}
