package com.examsystem.auth.security;

import com.examsystem.common.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 单进程认证入口限流：固定窗口内按来源和账号分别计数；摘要键不输出到日志，重启会重置。 */
@Component
public class AuthenticationThrottle {
    private static final long WINDOW_MILLIS = Duration.ofMinutes(10).toMillis();
    private final Map<String, Counter> counters = new HashMap<>();
    private final Clock clock;

    public AuthenticationThrottle(Clock clock) {
        this.clock = clock;
    }

    /** operation 由服务端固定；Web 和小程序共享涉及 Web 密码验证及考生注册的额度。 */
    public synchronized void check(String operation, String remoteAddress, String identifier) {
        long now = clock.millis();
        counters.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        consume(
                operation + ":ip:" + digest(remoteAddress),
                "register".equals(operation) ? 20 : 60,
                now);
        if (identifier != null) {
            consume(operation + ":account:" + digest(identifier), 10, now);
        }
    }

    private void consume(String key, int limit, long now) {
        Counter counter = counters.get(key);
        if (counter == null) {
            if (counters.size() >= 10000) {
                throw limited();
            }
            counter = new Counter(now + WINDOW_MILLIS);
            counters.put(key, counter);
        }
        if (++counter.attempts > limit) {
            throw limited();
        }
    }

    private static String digest(String input) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static ApiException limited() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "操作过于频繁，请稍后再试");
    }

    private static final class Counter {
        private final long expiresAt;
        private int attempts;

        private Counter(long expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
}
