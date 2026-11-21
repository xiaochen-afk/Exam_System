package com.examsystem.auth.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 统一密码编码参数和 UTC 时钟，避免各服务自行选择算法或依赖系统默认时区。 */
@Configuration
public class PasswordConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        // Argon2id：16 字节盐、32 字节哈希、并行度 1、19 MiB 内存、2 次迭代。
        return new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
