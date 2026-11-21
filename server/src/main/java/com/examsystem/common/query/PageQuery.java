package com.examsystem.common.query;

import com.examsystem.common.dto.PageResponse;
import com.examsystem.common.error.ApiException;
import java.util.List;

/** 共用分页输入；统一校验、LIKE 转义和偏移计算，响应 DTO 只承载结果。 */
public record PageQuery(int page, int size, String keyword) {
    public PageQuery {
        if (page < 1 || size < 1 || size > 100) {
            throw ApiException.invalid("page 至少为 1，size 必须在 1–100 之间");
        }
        keyword = escapeKeyword(keyword);
    }

    /** 用 long 计算偏移，避免较大页码的 int 乘法溢出。 */
    public long offset() {
        return (long) (page - 1) * size;
    }

    public <T> PageResponse<T> response(List<T> items, long total) {
        return new PageResponse<>(items, page, size, total);
    }

    private static String escapeKeyword(String input) {
        String keyword = input == null ? "" : input.strip();
        if (keyword.length() > 100) {
            throw ApiException.invalid("关键词最多为 100 字符");
        }
        // 与 XML 的 ESCAPE '!' 配套，让 %、_ 按字面查询，不能由调用方重复转义。
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
