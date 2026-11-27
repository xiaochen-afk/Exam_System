package com.examsystem.auth.config;

import com.examsystem.auth.mapper.AuthMapper;
import com.examsystem.auth.security.ApiSecurityErrorHandler;
import com.examsystem.auth.security.MiniProgramAccess;
import com.examsystem.auth.security.MiniProgramBearerFilter;
import com.examsystem.auth.security.SessionValidityFilter;
import com.examsystem.auth.service.MiniProgramAuthService;
import com.examsystem.common.error.ApiErrors;
import com.examsystem.portal.security.PublicPortalAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

/** 小程序与浏览器各自使用独立认证链，业务层读取同一种考生主体。 */
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
    @Order(1)
    SecurityFilterChain miniProgramSecurityFilterChain(
            HttpSecurity http,
            MiniProgramAuthService sessions,
            ApiErrors errors,
            ApiSecurityErrorHandler securityErrors)
            throws Exception {
        http.securityMatcher(MiniProgramAccess::matches)
                .sessionManagement(
                        config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(
                        config ->
                                config.securityContextRepository(
                                        new NullSecurityContextRepository()))
                // 此链只接收显式 Bearer 凭据，不读取浏览器的认证 Cookie。
                .csrf(config -> config.disable())
                .requestCache(config -> config.disable())
                .formLogin(config -> config.disable())
                .httpBasic(config -> config.disable())
                .logout(config -> config.disable())
                .authorizeHttpRequests(
                        config ->
                                config.requestMatchers(MiniProgramAccess::isPublic)
                                        .permitAll()
                                        .requestMatchers(MiniProgramAccess::isProtected)
                                        .authenticated()
                                        .anyRequest()
                                        .denyAll())
                .exceptionHandling(
                        config ->
                                config.authenticationEntryPoint(securityErrors)
                                        .accessDeniedHandler(securityErrors))
                .addFilterAfter(
                        new MiniProgramBearerFilter(sessions, errors),
                        SecurityContextHolderFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
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
