package com.examsystem.portal.dto;

/** 门户只读查询条件；枚举限制分类和状态，分页与关键词复用 PageQuery 校验。 */
public final class PortalQueries {
    private PortalQueries() {}

    public enum NoticeCategory {
        POLICY,
        NOTICE,
        GUIDE,
        FAQ
    }

    public enum RegistrationStatus {
        UPCOMING,
        OPEN,
        FULL,
        CLOSED,
        UNAVAILABLE
    }

    public record Notices(
            int page,
            int size,
            String keyword,
            NoticeCategory category,
            Boolean featured,
            Boolean hasAttachments) {}
}
