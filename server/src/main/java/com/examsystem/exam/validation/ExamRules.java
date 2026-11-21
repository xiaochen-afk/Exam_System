package com.examsystem.exam.validation;

import com.examsystem.common.error.ApiException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/** 可复用的输入规范化与业务校验；业务时间比较采用 UTC，错误不附原始输入。 */
public final class ExamRules {
    private ExamRules() {}

    public static String text(String value, int max, boolean optional) {
        if (value == null) throw ApiException.invalid("缺少必填字段");
        String result = value.strip();
        if ((!optional && result.isEmpty()) || result.length() > max)
            throw ApiException.invalid("文本字段为空或超出长度限制");
        return result;
    }

    public static String code(String value, boolean optional) {
        String result =
                text(value == null && optional ? "" : value, 32, optional).toUpperCase(Locale.ROOT);
        if (!result.matches(optional ? "[A-Z0-9_-]{0,32}" : "[A-Z0-9_-]{1,32}"))
            throw ApiException.invalid("代码仅支持字母、数字、下划线和连字符");
        return result;
    }

    public static String status(String value) {
        if (!List.of("ACTIVE", "INACTIVE").contains(value == null ? "" : value))
            throw ApiException.invalid("启用状态不正确");
        return value;
    }

    public static String qualification(String value) {
        if (!List.of("QUALIFIED", "UNQUALIFIED").contains(value == null ? "" : value))
            throw ApiException.invalid("考点资格状态不正确");
        return value;
    }

    public static long id(Long value) {
        if (value == null || value <= 0) throw ApiException.invalid("资源 ID 不正确");
        return value;
    }

    public static int capacity(Integer value) {
        if (value == null || value < 1 || value > 100000)
            throw ApiException.invalid("容量须为 1 至 100000 的整数");
        return value;
    }

    public static LocalDateTime utc(OffsetDateTime value) {
        if (value == null || value.getYear() < 1970 || value.getYear() > 9998)
            throw ApiException.invalid("时间须为有效的带时区时间");
        return value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    public static void times(
            LocalDateTime starts,
            LocalDateTime ends,
            LocalDateTime examStarts,
            LocalDateTime examEnds) {
        if (!starts.isBefore(ends) || !ends.isBefore(examStarts) || !examStarts.isBefore(examEnds))
            throw ApiException.invalid("时间须依次为报名开始、报名截止、考试开始、考试结束");
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "EXAM_STATE_CONFLICT", message);
    }
}
