package com.examsystem.portal.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;

/** 安全链和会话过滤器共用的匿名读取边界；新增路径需显式加入，不能放开整个资源子树。 */
public final class PublicPortalAccess {
    private static final Pattern PATH =
            Pattern.compile(
                    "/api/v1/(?:notices(?:/[^/]+(?:/attachments/[^/]+)?)?|exam-sessions(?:/[^/]+)?|sites)");

    private PublicPortalAccess() {}

    public static boolean matches(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod())) return false;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return PATH.matcher(path).matches();
    }
}
