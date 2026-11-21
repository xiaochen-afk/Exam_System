package com.examsystem.portal.model;

/** 发布状态与发布时间分开，保留旧资讯的未来发布时间。 */
public enum NoticeStatus {
    DRAFT,
    PUBLISHED,
    WITHDRAWN
}
