package com.examsystem.auth.config;

import com.examsystem.auth.mapper.AuthMapper;
import com.examsystem.auth.security.ApiSecurityErrorHandler;
import com.examsystem.auth.security.SessionValidityFilter;
import com.examsystem.common.error.ApiErrors;
import com.examsystem.portal.security.PublicPortalAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

/** 浏览器会话安全策略；这里只装配过滤链，错误输出交给专用处理器。 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    // region 会话与 CSRF 仓库

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    HttpSessionCsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    // endregion

    // region 安全过滤链与 API 错误处理

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository contexts,
            HttpSessionCsrfTokenRepository csrf,
            AuthMapper mapper,
            ApiErrors errors,
            ApiSecurityErrorHandler securityErrors)
            throws Exception {
        http.securityContext(config -> config.securityContextRepository(contexts))
                .csrf(config -> config.csrfTokenRepository(csrf))
                .requestCache(config -> config.disable())
                .formLogin(config -> config.disable())
                .httpBasic(config -> config.disable())
                .logout(config -> config.disable())
                .authorizeHttpRequests(
                        config ->
                                config.requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/csrf-tokens",
                                                "/actuator/health")
                                        .permitAll()
                                        .requestMatchers(PublicPortalAccess::matches)
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/candidate-accounts",
                                                "/api/v1/web-sessions")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.DELETE, "/api/v1/web-sessions/current")
                                        .permitAll()
                                        .requestMatchers("/api/v1/**")
                                        .authenticated()
                                        .anyRequest()
                                        .denyAll())
                .exceptionHandling(
                        config ->
                                config.authenticationEntryPoint(securityErrors)
                                        .accessDeniedHandler(securityErrors))
                .addFilterAfter(
                        new SessionValidityFilter(mapper, errors),
                        SecurityContextHolderFilter.class);
        return http.build();
    }
    // endregion

}
