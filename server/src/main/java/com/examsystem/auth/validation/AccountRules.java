package com.examsystem.auth.validation;

import com.examsystem.common.error.ApiException;
import java.util.Locale;

/** 账号输入的无状态规则；仅校验格式和规范化，不访问数据库或判断权限。 */
public final class AccountRules {
    private AccountRules() {}

    /** 统一大小写和首尾空白，与数据库规范化唯一键保持同一口径。 */
    public static String identifier(String input) {
        if (input == null || !input.strip().matches("[A-Za-z0-9._-]{4,32}")) {
            throw ApiException.invalid("账号须为 4–32 位字母、数字、点、下划线或连字符");
        }
        return input.strip().toLowerCase(Locale.ROOT);
    }

    /** 仅用于新密码校验；登录不按新密码策略泄露账号状态。 */
    public static String password(String input) {
        if (input == null
                || input.length() < 8
                || input.length() > 64
                || !input.matches("(?s).*[A-Za-z].*")
                || !input.matches("(?s).*[0-9].*")) {
            throw ApiException.invalid("密码须为 8–64 位，且包含英文字母和数字");
        }
        return input;
    }

    public static String displayName(String input) {
        if (input == null || input.strip().isEmpty() || input.strip().length() > 50) {
            throw ApiException.invalid("显示名称须为 1–50 字符");
        }
        return input.strip();
    }

    /** 创建、修改和目录筛选复用相同状态字典；不接受客户端自定义状态。 */
    public static String status(String value) {
        if (!("ACTIVE".equals(value) || "INACTIVE".equals(value))) {
            throw ApiException.invalid("状态须为 ACTIVE 或 INACTIVE");
        }
        return value;
    }
}
