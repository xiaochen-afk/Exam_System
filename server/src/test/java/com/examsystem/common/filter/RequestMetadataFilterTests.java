package com.examsystem.common.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.examsystem.support.LogCapture;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestMetadataFilterTests {
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void requestIdMatchesResponseWithoutLoggingUntrustedHeadersBodyOrQuery() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/web-sessions");
        request.addHeader("X-Request-ID", "untrusted-request-id");
        request.addHeader("Cookie", "JSESSIONID=fictional-cookie-secret");
        request.setQueryString("password=fictional-query-secret");
        request.setContent(
                "fictional-body-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();
        MDC.put("unrelated", "upstream-value");

        try (var logs = new LogCapture()) {
            new RequestMetadataFilter()
                    .doFilter(
                            request,
                            response,
                            (req, res) -> {
                                assertEquals(
                                        response.getHeader("X-Request-ID"), MDC.get("requestId"));
                                response.setStatus(201);
                            });
            assertNotEquals("untrusted-request-id", response.getHeader("X-Request-ID"));
            var event =
                    logs.events().stream()
                            .filter(
                                    item ->
                                            item.getFormattedMessage()
                                                    .contains("event=http_request_completed"))
                            .findFirst()
                            .orElseThrow();
            assertEquals(
                    response.getHeader("X-Request-ID"), event.getMDCPropertyMap().get("requestId"));
            assertFalse(event.getFormattedMessage().contains("secret"));
            assertFalse(event.getFormattedMessage().contains("untrusted-request-id"));
            assertNull(MDC.get("requestId"));
            assertEquals("upstream-value", MDC.get("unrelated"));
        }
    }

    @Test
    void failedRequestsClearTheirIdAndRestoreAnyUpstreamRequestId() {
        for (String previous : new String[] {null, "upstream-request-id"}) {
            if (previous != null) {
                MDC.put("requestId", previous);
            }
            var request = new MockHttpServletRequest("GET", "/api/v1/me");
            var response = new MockHttpServletResponse();
            assertThrows(
                    ServletException.class,
                    () ->
                            new RequestMetadataFilter()
                                    .doFilter(
                                            request,
                                            response,
                                            (req, res) -> {
                                                assertNotEquals(previous, MDC.get("requestId"));
                                                throw new ServletException(
                                                        "fictional-sensitive-exception");
                                            }));
            assertEquals(previous, MDC.get("requestId"));
        }
    }
}
