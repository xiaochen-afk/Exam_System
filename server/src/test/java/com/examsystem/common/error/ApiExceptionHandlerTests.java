package com.examsystem.common.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import com.examsystem.support.LogCapture;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.ObjectMapper;

class ApiExceptionHandlerTests {
    @Test
    void unexpectedExceptionLogsTypeAndRequestIdWithoutSensitiveMessagesOrCauses() {
        var request = new MockHttpServletRequest("POST", "/api/v1/candidate-accounts");
        var handler = new ApiExceptionHandler(new ApiErrors(new ObjectMapper()));
        var error =
                new IllegalStateException(
                        "fictional-sensitive-message",
                        new RuntimeException("fictional-sensitive-cause"));
        try (var logs = new LogCapture()) {
            var response = handler.unexpected(error, request);
            assertEquals(500, response.getStatusCode().value());
            var event =
                    logs.events().stream()
                            .filter(item -> item.getLevel() == Level.ERROR)
                            .findFirst()
                            .orElseThrow();
            assertEquals("INTERNAL_ERROR", response.getBody().code());
            assertTrue(event.getFormattedMessage().contains(response.getBody().requestId()));
            assertTrue(event.getFormattedMessage().contains(IllegalStateException.class.getName()));
            assertFalse(event.getFormattedMessage().contains("fictional-sensitive"));
            assertNull(event.getThrowableProxy());
        }
    }
}
