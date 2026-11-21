package com.examsystem.portal.validation;

import com.examsystem.common.error.ApiException;
import com.examsystem.portal.dto.AdminNoticeDtos;
import com.examsystem.portal.entity.Notice;

/** 内容字段统一清理和限长。发布时对旧记录也重新检查必填内容。 */
public final class NoticeContentRules {
    private NoticeContentRules() {}

    public static void apply(Notice row, AdminNoticeDtos.Content changes) {
        if (changes.category() != null) row.setCategory(changes.category().name());
        if (changes.title() != null) row.setTitle(changes.title().strip());
        if (changes.summary() != null) row.setSummary(changes.summary().strip());
        if (changes.body() != null) row.setBody(changes.body().strip());
        if (changes.publisher() != null) row.setPublisherLabel(changes.publisher().strip());
        if (changes.pinned() != null) row.setPinned(changes.pinned());
        if (changes.featured() != null) row.setFeatured(changes.featured());
        validate(row);
    }

    public static void validate(Notice row) {
        if (row.getCategory() == null) throw ApiException.invalid("请选择资讯分类");
        required(row.getTitle(), 200, "标题");
        required(row.getSummary(), 500, "摘要");
        // 16000 个 UTF-16 单元最多占 48000 字节，保留在 MySQL TEXT 的容量内。
        required(row.getBody(), 16000, "正文");
        required(row.getPublisherLabel(), 100, "发布单位");
    }

    private static void required(String value, int max, String label) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw ApiException.invalid(label + "须为 1–" + max + " 字符");
        }
    }
}
