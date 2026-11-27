package com.examsystem.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** 只有小程序专用端点和明确列出的考生自助端点接受小程序认证。 */
public final class MiniProgramAccess {
    public static final String ROOT = "/api/v1/mini-program";

    private static final PathPatternRequestMatcher.Builder PATH =
            PathPatternRequestMatcher.withDefaults();
    private static final RequestMatcher NAMESPACE = PATH.matcher(ROOT + "/**");
    private static final RequestMatcher PUBLIC =
            new OrRequestMatcher(
                    PATH.matcher(HttpMethod.POST, ROOT + "/sessions"),
                    PATH.matcher(HttpMethod.POST, ROOT + "/account-links"),
                    PATH.matcher(HttpMethod.POST, ROOT + "/candidate-accounts"));
    private static final RequestMatcher PRIVATE =
            new OrRequestMatcher(
                    PATH.matcher(HttpMethod.GET, ROOT + "/me"),
                    PATH.matcher(HttpMethod.POST, ROOT + "/web-credentials"),
                    PATH.matcher(HttpMethod.DELETE, ROOT + "/sessions/current"));
    private static final RequestMatcher CANDIDATE =
            new OrRequestMatcher(
                    PATH.matcher(HttpMethod.GET, "/api/v1/me/candidate-profile"),
                    PATH.matcher(HttpMethod.PUT, "/api/v1/me/candidate-profile"),
                    PATH.matcher(HttpMethod.GET, "/api/v1/me/registrations"),
                    PATH.matcher(HttpMethod.GET, "/api/v1/me/registrations/{id}"),
                    PATH.matcher(HttpMethod.GET, "/api/v1/schools"),
                    PATH.matcher(HttpMethod.GET, "/api/v1/exam-sessions/{id}/registration-options"),
                    PATH.matcher(HttpMethod.POST, "/api/v1/exam-sessions/{id}/registrations"));

    private MiniProgramAccess() {}

    public static boolean matches(HttpServletRequest request) {
        return NAMESPACE.matches(request)
                || (request.getHeader(HttpHeaders.AUTHORIZATION) != null
                        && CANDIDATE.matches(request));
    }

    public static boolean isPublic(HttpServletRequest request) {
        return PUBLIC.matches(request);
    }

    public static boolean isProtected(HttpServletRequest request) {
        return PRIVATE.matches(request) || CANDIDATE.matches(request);
    }
}
