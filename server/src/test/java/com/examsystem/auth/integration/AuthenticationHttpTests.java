package com.examsystem.auth.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.examsystem.ExamSystemServerApplication;
import com.examsystem.auth.mapper.AdminMapper;
import com.examsystem.auth.mapper.PermissionMapper;
import com.examsystem.auth.service.AdminService;
import com.examsystem.auth.service.AuthService;
import com.examsystem.auth.service.PermissionService;
import com.examsystem.common.error.ApiException;
import com.examsystem.support.LogCapture;
import com.examsystem.support.MySqlIntegrationSupport;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AuthenticationHttpTests.TimeConfiguration.class)
class AuthenticationHttpTests extends MySqlIntegrationSupport {
    // region 测试依赖与生命周期

    private static final String PASSWORD = "FictionalPass2026";
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired AuthService accounts;
    @Autowired AdminService administrators;
    @Autowired AdminMapper adminMapper;
    @Autowired PermissionService permissions;
    @Autowired PermissionMapper scopes;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestClock clock;
    @Autowired Environment environment;
    @Autowired PlatformTransactionManager transactions;
    private final List<Client> clients = new ArrayList<>();

    @BeforeEach
    void reset() throws Exception {
        clearBusinessData();
        clock.advance(Duration.ofMinutes(11));
    }

    @AfterEach
    void closeClients() {
        clients.forEach(Client::close);
        clients.clear();
    }

    // endregion

    // region 匿名访问与 CSRF 基础

    @Test
    void anonymousRequestReturnsJson401WithRequestIdAndNoStore() throws Exception {
        Result result = client().send("GET", "/me", null, null);
        assertEquals(401, result.status());
        assertEquals("SESSION_REQUIRED", result.body().path("code").asText());
        assertEquals(
                result.body().path("requestId").asText(),
                result.response().headers().firstValue("X-Request-ID").orElseThrow());
        assertEquals(
                "no-store", result.response().headers().firstValue("Cache-Control").orElseThrow());
    }

    @Test
    void anonymousWritesRequireCsrfIncludingLoginRegistrationAndLogout() throws Exception {
        Client client = client();
        for (String path :
                List.of("/candidate-accounts", "/web-sessions", "/web-sessions/current")) {
            Result result =
                    client.send(path.endsWith("current") ? "DELETE" : "POST", path, Map.of(), null);
            assertEquals(403, result.status());
            assertEquals("CSRF_INVALID", result.body().path("code").asText());
        }
    }

    // endregion

    // region 考生注册、密码与并发

    @Test
    void registrationPersistsOnlyCandidateAndHashAndEmptyProfile() throws Exception {
        Client client = client();
        Result result = client.write("POST", "/candidate-accounts", registration("  Test.User  "));
        assertEquals(201, result.status());
        long id = Long.parseLong(result.body().path("id").asText());
        assertEquals(1, result.body().size());
        assertEquals(
                "CANDIDATE",
                jdbc.queryForObject(
                        "SELECT account_kind FROM app_user WHERE id=?", String.class, id));
        assertEquals(
                "test.user",
                jdbc.queryForObject(
                        "SELECT identifier FROM web_credential WHERE user_id=?", String.class, id));
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM candidate_profile WHERE candidate_id=? AND candidate_type IS NULL AND school_id IS NULL",
                        Long.class,
                        id));
        String hash =
                jdbc.queryForObject(
                        "SELECT password_hash FROM web_credential WHERE user_id=?",
                        String.class,
                        id);
        assertTrue(hash.startsWith("$argon2id$"));
        assertTrue(hash.contains("m=19456,t=2,p=1"));
        assertTrue(passwords.matches(PASSWORD, hash));
        assertFalse(passwords.matches("Incorrect2026", hash));
        assertFalse(hash.contains(PASSWORD));
        assertEquals(401, client.send("GET", "/me", null, null).status());
    }

    @Test
    void eachPasswordHashHasIndependentSalt() {
        String first = passwords.encode(PASSWORD);
        String second = passwords.encode(PASSWORD);
        assertNotEquals(first, second);
        assertTrue(passwords.matches(PASSWORD, first));
        assertTrue(passwords.matches(PASSWORD, second));
    }

    @Test
    void invalidRegistrationAndInjectedRolesCannotCreateAccounts() throws Exception {
        Client client = client();
        var invalid =
                List.of(
                        Map.of("identifier", "abc", "password", PASSWORD, "displayName", "测试考生"),
                        Map.of(
                                "identifier",
                                "valid.user",
                                "password",
                                "weak",
                                "displayName",
                                "测试考生"),
                        Map.of(
                                "identifier",
                                "valid.user",
                                "password",
                                PASSWORD,
                                "displayName",
                                "  "),
                        Map.of(
                                "identifier",
                                "valid.user",
                                "password",
                                PASSWORD,
                                "displayName",
                                "测试考生",
                                "roles",
                                List.of("ADMIN")));
        for (var body : invalid) {
            assertEquals(400, client.write("POST", "/candidate-accounts", body).status());
        }
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Long.class));
    }

    @Test
    void duplicateRegistrationIsCaseInsensitiveAndRollsBackAllRows() throws Exception {
        Client client = client();
        assertEquals(
                201,
                client.write("POST", "/candidate-accounts", registration("Duplicate.User"))
                        .status());
        Result duplicate =
                client.write("POST", "/candidate-accounts", registration(" duplicate.user "));
        assertEquals(409, duplicate.status());
        assertEquals("IDENTIFIER_TAKEN", duplicate.body().path("code").asText());
        for (String table : List.of("app_user", "candidate_profile", "web_credential")) {
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
        }
    }

    @Test
    void concurrentRegistrationCreatesExactlyOneCompleteAccount() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<java.util.concurrent.Future<Integer>> outcomes = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                outcomes.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    assertTrue(start.await(10, TimeUnit.SECONDS));
                                    try {
                                        accounts.register("concurrent.user", PASSWORD, "虚构并发考生");
                                        return 201;
                                    } catch (ApiException conflict) {
                                        return conflict.status().value();
                                    }
                                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            var statuses =
                    List.of(
                                    outcomes.get(0).get(20, TimeUnit.SECONDS),
                                    outcomes.get(1).get(20, TimeUnit.SECONDS))
                            .stream()
                            .sorted()
                            .toList();
            assertEquals(List.of(201, 409), statuses);
        }
        for (String table : List.of("app_user", "web_credential", "candidate_profile")) {
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
        }
    }

    // endregion

    // region 会话生命周期与登录拒绝

    @Test
    void loginRotatesSessionAndLogoutInvalidatesOldCookie() throws Exception {
        accounts.register("session.user", PASSWORD, "虚构考生");
        Client client = client();
        String oldCsrf = client.csrf();
        String anonymousCookie = client.sessionId();
        Result login = client.login("session.user", "CANDIDATE");
        assertEquals(201, login.status());
        assertNotEquals(anonymousCookie, client.sessionId());
        assertTrue(
                login.response().headers().allValues("Set-Cookie").stream()
                        .anyMatch(
                                cookie ->
                                        cookie.toLowerCase().contains("httponly")
                                                && cookie.toLowerCase().contains("samesite=lax")));
        assertEquals(401, withCookie(anonymousCookie).send("GET", "/me", null, null).status());
        assertEquals(200, client.send("GET", "/me", null, null).status());
        assertEquals(403, client.send("DELETE", "/web-sessions/current", null, oldCsrf).status());
        String authenticatedCookie = client.sessionId();
        assertEquals(204, client.write("DELETE", "/web-sessions/current", null).status());
        assertEquals(401, withCookie(authenticatedCookie).send("GET", "/me", null, null).status());
        assertEquals(401, client.send("GET", "/me", null, null).status());
    }

    @Test
    void csrfTokenCannotBeReusedAcrossDifferentSessions() throws Exception {
        Client first = client();
        Client second = client();
        second.csrf();
        assertEquals(
                403,
                second.send("POST", "/candidate-accounts", registration("cross.csrf"), first.csrf())
                        .status());
        assertEquals(
                201,
                second.write("POST", "/candidate-accounts", registration("cross.csrf")).status());
    }

    @Test
    void loginErrorsDoNotRevealAccountExistenceOrGrantWrongPortal() throws Exception {
        accounts.register("login.user", PASSWORD, "虚构考生");
        Client client = client();
        String message = null;
        for (var input :
                List.of(
                        Map.of(
                                "identifier",
                                "missing.user",
                                "password",
                                PASSWORD,
                                "portal",
                                "CANDIDATE"),
                        Map.of(
                                "identifier",
                                "login.user",
                                "password",
                                "WrongPass2026",
                                "portal",
                                "CANDIDATE"),
                        Map.of(
                                "identifier",
                                "login.user",
                                "password",
                                PASSWORD,
                                "portal",
                                "STAFF"))) {
            Result result = client.write("POST", "/web-sessions", input);
            assertEquals(401, result.status());
            assertEquals("INVALID_CREDENTIALS", result.body().path("code").asText());
            if (message != null) {
                assertEquals(message, result.body().path("message").asText());
            }
            message = result.body().path("message").asText();
            assertFalse(result.response().body().contains(PASSWORD));
        }
    }

    @Test
    void accountDisableAndReenableCannotReviveOldSession() throws Exception {
        long id = accounts.register("disabled.user", PASSWORD, "虚构停用考生");
        Client client = client();
        assertEquals(201, client.login("disabled.user", "CANDIDATE").status());
        String cookie = client.sessionId();
        jdbc.update(
                "UPDATE app_user SET status='INACTIVE', session_version=session_version+1 WHERE id=?",
                id);
        assertEquals(401, client.send("GET", "/me", null, null).status());
        assertEquals(401, client.login("disabled.user", "CANDIDATE").status());
        jdbc.update("UPDATE app_user SET status='ACTIVE' WHERE id=?", id);
        assertEquals(401, withCookie(cookie).send("GET", "/me", null, null).status());
        assertEquals(201, client.login("disabled.user", "CANDIDATE").status());
    }

    // endregion

    // region 管理员初始化、授权与范围隔离

    @Test
    void systemAdministratorHasNoImplicitExamDataPermission() throws Exception {
        Client admin = administrator();
        Result identity = admin.send("GET", "/me", null, null);
        assertEquals("ADMIN", identity.body().path("roles").get(0).asText());
        assertFalse(identity.body().path("permissions").toString().contains("sessions.read"));
        assertEquals(200, admin.send("GET", "/admin/users", null, null).status());
        assertEquals(200, admin.send("GET", "/admin/roles", null, null).status());
        assertEquals(200, admin.send("GET", "/admin/audit-logs", null, null).status());
        assertEquals(403, admin.send("GET", "/admin/exam-sessions", null, null).status());
        var metrics = admin.send("GET", "/admin/dashboard", null, null).body().path("metrics");
        assertEquals(1, metrics.size());
        assertEquals("users", metrics.get(0).path("key").asText());
    }

    @Test
    void bootstrapApplicationCanStartWithoutHttpServer() {
        SpringApplication application = new SpringApplication(ExamSystemServerApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (var context =
                application.run(
                        "--spring.datasource.url="
                                + environment.getProperty("spring.datasource.url"))) {
            long id =
                    context.getBean(AdminService.class)
                            .bootstrap("console.admin", PASSWORD, "虚构控制台管理员");
            assertTrue(id > 0);
            assertFalse(context.containsBean("authController"));
        }
    }

    @Test
    void administratorBootstrapIsOneTimeAndDoesNotGrantExamRights() {
        long id = administrators.bootstrap("initial.admin", PASSWORD, "虚构管理员");
        assertThrows(
                ApiException.class,
                () -> administrators.bootstrap("other.admin", PASSWORD, "另一管理员"));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Long.class));
        assertEquals(
                5L,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM staff_grant_permission p JOIN staff_grant g ON g.id=p.grant_id WHERE g.staff_id=?",
                        Long.class,
                        id));
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_event WHERE action_code='admins.bootstrap'",
                        Long.class));
    }

    @Test
    void grantRevocationAffectsAlreadyAuthenticatedStaffAndIsAudited() throws Exception {
        Client admin = administrator();
        long target =
                createStaff(
                        admin,
                        "catalog.staff",
                        List.of(grant("ORGANIZER", "CATALOG", null, null, "subjects.read")));
        Client staff = client();
        assertEquals(201, staff.login("catalog.staff", "STAFF").status());
        assertTrue(
                staff.send("GET", "/me", null, null)
                        .body()
                        .path("permissions")
                        .toString()
                        .contains("subjects.read"));
        assertEquals(
                204,
                admin.write("PATCH", "/admin/users/" + target, Map.of("grants", List.of()))
                        .status());
        assertEquals(0, staff.send("GET", "/me", null, null).body().path("permissions").size());
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM staff_grant WHERE staff_id=? AND revoked_at IS NOT NULL",
                        Long.class,
                        target));
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_event WHERE action_code='users.update' AND result='SUCCESS'",
                        Long.class));
    }

    @Test
    void actionsAndScopesCannotBeCombinedAcrossTwoGrantsAndCountsAreFiltered() throws Exception {
        long[] sessions = sessionFixtures();
        Client admin = administrator();
        long target =
                createStaff(
                        admin,
                        "site.staff",
                        List.of(
                                grant(
                                        "SITE_MANAGER",
                                        "SESSION",
                                        null,
                                        sessions[0],
                                        "sessions.read"),
                                grant(
                                        "SITE_MANAGER",
                                        "SESSION",
                                        null,
                                        sessions[1],
                                        "arrangements.write")));
        Client staff = client();
        staff.login("site.staff", "STAFF");
        Result list = staff.send("GET", "/admin/exam-sessions", null, null);
        assertEquals(200, list.status());
        assertEquals(1, list.body().path("total").asInt());
        assertEquals(1, list.body().path("items").size());
        assertEquals(
                1,
                staff.send("GET", "/admin/dashboard", null, null)
                        .body()
                        .path("metrics")
                        .get(0)
                        .path("value")
                        .asInt());
        assertEquals(
                Long.toString(sessions[0]), list.body().path("items").get(0).path("id").asText());
        assertTrue(permissions.allowsSession(target, "sessions.read", scopes.session(sessions[0])));
        assertFalse(
                permissions.allowsSession(target, "sessions.read", scopes.session(sessions[1])));
        assertFalse(
                permissions.allowsSession(
                        target, "arrangements.write", scopes.session(sessions[0])));
        assertEquals(
                0,
                staff.send("GET", "/admin/exam-sessions?page=2&size=1", null, null)
                        .body()
                        .path("items")
                        .size());
        assertEquals(
                204,
                admin.write("PATCH", "/admin/users/" + target, Map.of("grants", List.of()))
                        .status());
        assertEquals(403, staff.send("GET", "/admin/exam-sessions", null, null).status());
    }

    @Test
    void invalidGrantCannotLeavePartialStaffAccountAndRecordsFailure() throws Exception {
        Client admin = administrator();
        Result result =
                admin.write(
                        "POST",
                        "/admin/users",
                        Map.of(
                                "identifier",
                                "illegal.staff",
                                "initialPassword",
                                PASSWORD,
                                "displayName",
                                "虚构人员",
                                "grants",
                                List.of(
                                        grant(
                                                "SITE_MANAGER",
                                                "CATALOG",
                                                null,
                                                null,
                                                "users.write"))));
        assertEquals(400, result.status());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Long.class));
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_event WHERE action_code='users.create' AND result='FAILED'",
                        Long.class));
    }

    @Test
    void selfPrivilegeEditingAndGrantingBootstrapOnlyPermissionAreRejected() throws Exception {
        Client admin = administrator();
        String id = admin.send("GET", "/me", null, null).body().path("id").asText();
        assertEquals(
                403,
                admin.write("PATCH", "/admin/users/" + id, Map.of("grants", List.of())).status());
        assertEquals(
                403,
                admin.write(
                                "POST",
                                "/admin/users",
                                Map.of(
                                        "identifier",
                                        "super.staff",
                                        "initialPassword",
                                        PASSWORD,
                                        "displayName",
                                        "虚构人员",
                                        "grants",
                                        List.of(
                                                grant(
                                                        "SYSTEM_ADMIN",
                                                        "SYSTEM",
                                                        null,
                                                        null,
                                                        "admins.manage"))))
                        .status());
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM app_user WHERE status='ACTIVE'", Long.class));
    }

    @Test
    void ordinaryAdministratorCannotModifyOtherAdministrators() throws Exception {
        Client superAdmin = administrator();
        long target =
                createStaff(
                        superAdmin,
                        "ordinary.admin",
                        List.of(
                                grant(
                                        "SYSTEM_ADMIN",
                                        "SYSTEM",
                                        null,
                                        null,
                                        "users.read",
                                        "users.write")));
        String superId = superAdmin.send("GET", "/me", null, null).body().path("id").asText();
        Client ordinary = client();
        ordinary.login("ordinary.admin", "STAFF");
        assertEquals(
                403,
                ordinary.write("PATCH", "/admin/users/" + superId, Map.of("status", "INACTIVE"))
                        .status());
        assertEquals(
                403,
                ordinary.write("PATCH", "/admin/users/" + target, Map.of("status", "INACTIVE"))
                        .status());
    }

    @Test
    void concurrentAdministratorDisablesLeaveOneControllingAccount() throws Exception {
        Client first = administrator();
        long firstId =
                Long.parseLong(first.send("GET", "/me", null, null).body().path("id").asText());
        long secondId =
                createStaff(
                        first,
                        "second.admin",
                        List.of(
                                grant(
                                        "SYSTEM_ADMIN",
                                        "SYSTEM",
                                        null,
                                        null,
                                        "users.read",
                                        "users.write")));
        // 仅在隔离测试库模拟受控部署授予第二个管理入口，HTTP 本身禁止授予 admins.manage。
        long grantId = adminMapper.grantId(secondId, "SYSTEM_ADMIN", "SYSTEM", 0);
        adminMapper.grantPermission(grantId, "SYSTEM_ADMIN", "SYSTEM", "admins.manage");
        Client second = client();
        second.login("second.admin", "STAFF");
        first.csrf();
        second.csrf();
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one =
                    pool.submit(
                            () -> {
                                start.await();
                                return first.write(
                                                "PATCH",
                                                "/admin/users/" + secondId,
                                                Map.of("status", "INACTIVE"))
                                        .status();
                            });
            var two =
                    pool.submit(
                            () -> {
                                start.await();
                                return second.write(
                                                "PATCH",
                                                "/admin/users/" + firstId,
                                                Map.of("status", "INACTIVE"))
                                        .status();
                            });
            start.countDown();
            assertEquals(
                    List.of(204, 401),
                    List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS)).stream()
                            .sorted()
                            .toList());
        }
        assertEquals(1L, adminMapper.controllingAdministratorCount());
    }

    @Test
    void candidateCannotUseInternalAccountOrRoleEndpoints() throws Exception {
        accounts.register("candidate.user", PASSWORD, "虚构考生");
        Client candidate = client();
        candidate.login("candidate.user", "CANDIDATE");
        assertEquals(403, candidate.send("GET", "/admin/users", null, null).status());
        assertEquals(403, candidate.send("GET", "/admin/roles", null, null).status());
        assertEquals(
                403,
                candidate
                        .write(
                                "POST",
                                "/admin/users",
                                Map.of(
                                        "identifier",
                                        "forged.staff",
                                        "initialPassword",
                                        PASSWORD,
                                        "displayName",
                                        "虚构人员",
                                        "grants",
                                        List.of()))
                        .status());
    }

    @Test
    void staffDisableImmediatelyInvalidatesSessionAndRestoreRequiresLogin() throws Exception {
        Client admin = administrator();
        long target = createStaff(admin, "revoked.staff", List.of());
        Client staff = client();
        staff.login("revoked.staff", "STAFF");
        String old = staff.sessionId();
        assertEquals(
                204,
                admin.write("PATCH", "/admin/users/" + target, Map.of("status", "INACTIVE"))
                        .status());
        assertEquals(401, staff.send("GET", "/me", null, null).status());
        assertEquals(
                204,
                admin.write("PATCH", "/admin/users/" + target, Map.of("status", "ACTIVE"))
                        .status());
        assertEquals(401, withCookie(old).send("GET", "/me", null, null).status());
        assertEquals(201, staff.login("revoked.staff", "STAFF").status());
    }

    // endregion

    // region 输入错误与登录限流

    @Test
    void invalidPaginationAndMalformedJsonProduceControlled400() throws Exception {
        Client admin = administrator();
        assertEquals(
                400,
                admin.write("PATCH", "/admin/users/-1", Map.of("status", "INACTIVE")).status());
        assertEquals(400, admin.send("GET", "/admin/users?page=0", null, null).status());
        assertEquals(400, admin.send("GET", "/admin/users?size=101", null, null).status());
        assertEquals(400, admin.send("GET", "/admin/users?page=oops", null, null).status());
        assertEquals(
                400,
                admin.write("PATCH", "/admin/users/1", Map.of("roleCodes", List.of("SYSTEM_ADMIN")))
                        .status());
        assertEquals(404, admin.send("GET", "/not-implemented", null, null).status());
        assertEquals(405, admin.write("POST", "/admin/dashboard", Map.of()).status());
    }

    @Test
    void repeatedLoginAttemptsAreRateLimitedAndRecoverAfterWindow() throws Exception {
        Client client = client();
        Map<String, String> login =
                Map.of("identifier", "limited.user", "password", PASSWORD, "portal", "CANDIDATE");
        for (int attempt = 0; attempt < 10; attempt++) {
            assertEquals(401, client.write("POST", "/web-sessions", login).status());
        }
        assertEquals(429, client.write("POST", "/web-sessions", login).status());
        clock.advance(Duration.ofMinutes(11));
        assertEquals(401, client.write("POST", "/web-sessions", login).status());
    }

    // endregion

    // region 授权表单选项

    @Test
    void grantOptionsExposeOnlyDelegableMetadataAndRespectSystemPermissions() throws Exception {
        sessionFixtures();
        assertEquals(1, adminMapper.projectOptions().size());
        assertEquals(2, adminMapper.sessionOptions().size());
        Client root = administrator();
        Result options = root.send("GET", "/admin/grant-options", null, null);
        assertEquals(200, options.status());
        assertEquals(3, options.body().path("roles").size());
        assertEquals(1, options.body().path("projects").size());
        assertEquals(2, options.body().path("sessions").size());
        assertFalse(options.response().body().contains("admins.manage"));
        assertFalse(options.response().body().contains("capacity"));
        assertFalse(options.response().body().contains("registrationCount"));

        createStaff(
                root,
                "writer.options",
                List.of(grant("SYSTEM_ADMIN", "SYSTEM", null, null, "users.write")));
        Client writer = client();
        assertEquals(201, writer.login("writer.options", "STAFF").status());
        Result writerOptions = writer.send("GET", "/admin/grant-options", null, null);
        assertEquals(200, writerOptions.status());
        assertEquals(2, writerOptions.body().path("roles").size());
        assertFalse(writerOptions.response().body().contains("SYSTEM_ADMIN"));
        assertEquals(403, writer.send("GET", "/admin/roles", null, null).status());
        accounts.register("options.candidate", PASSWORD, "虚构选项考生");
        Client candidate = client();
        candidate.login("options.candidate", "CANDIDATE");
        assertEquals(403, candidate.send("GET", "/admin/grant-options", null, null).status());
        assertEquals(401, client().send("GET", "/admin/grant-options", null, null).status());
    }

    @Test
    void grantOptionNamesSupportAsciiCodesAndIndependentProjects() {
        sessionFixtures();
        jdbc.update(
                "INSERT INTO exam_project(subject_id,application_code) SELECT subject_id,NULL FROM exam_project LIMIT 1");
        var options = adminMapper.projectOptions();
        assertEquals(2, options.size());
        assertTrue(options.stream().anyMatch(option -> option.name().contains("独立报考项目")));
        assertTrue(options.stream().anyMatch(option -> option.name().contains("TEST-PROJECT")));
    }

    // endregion

    // region 测试数据与业务操作辅助

    private Client administrator() throws Exception {
        administrators.bootstrap("initial.admin", PASSWORD, "虚构管理员");
        Client client = client();
        assertEquals(201, client.login("initial.admin", "STAFF").status());
        return client;
    }

    private long createStaff(Client admin, String identifier, List<Map<String, Object>> grants)
            throws Exception {
        Result result =
                admin.write(
                        "POST",
                        "/admin/users",
                        Map.of(
                                "identifier",
                                identifier,
                                "initialPassword",
                                PASSWORD,
                                "displayName",
                                "虚构内部人员",
                                "grants",
                                grants));
        assertEquals(
                201,
                result.status(),
                () -> "Staff creation failed with " + result.body().path("code").asText());
        return Long.parseLong(result.body().path("id").asText());
    }

    private static Map<String, Object> grant(
            String role, String scope, Long project, Long session, String... actions) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("roleCode", role);
        result.put("scopeType", scope);
        result.put("projectId", project);
        result.put("sessionId", session);
        result.put("permissionCodes", List.of(actions));
        return result;
    }

    private static Map<String, String> registration(String identifier) {
        return Map.of("identifier", identifier, "password", PASSWORD, "displayName", "虚构测试考生");
    }

    private long[] sessionFixtures() {
        jdbc.update("INSERT INTO exam_subject(code,name) VALUES('TEST-SUBJECT','虚构科目')");
        long subject =
                jdbc.queryForObject(
                        "SELECT id FROM exam_subject WHERE code='TEST-SUBJECT'", Long.class);
        jdbc.update(
                "INSERT INTO exam_project(subject_id,application_code) VALUES(?,'TEST-PROJECT')",
                subject);
        long project =
                jdbc.queryForObject(
                        "SELECT id FROM exam_project WHERE application_code='TEST-PROJECT'",
                        Long.class);
        long[] sessions = new long[2];
        for (int i = 0; i < 2; i++) {
            String name = "虚构考点" + i;
            jdbc.update(
                    "INSERT INTO exam_site(name,address,qualification_status) VALUES(?,'虚构地址','QUALIFIED')",
                    name);
            long site =
                    jdbc.queryForObject("SELECT id FROM exam_site WHERE name=?", Long.class, name);
            jdbc.update(
                    "INSERT INTO exam_project_site(project_id,site_id,registration_open) VALUES(?,?,1)",
                    project,
                    site);
            jdbc.update(
                    """
                    INSERT INTO exam_session(project_id,site_id,name,registration_starts_at,registration_ends_at,
                    exam_starts_at,exam_ends_at,capacity,status)
                    VALUES(?, ?, ?, '2026-11-01 00:00:00', '2026-11-30 00:00:00',
                    '2026-12-10 01:00:00', '2026-12-10 03:00:00', 10, 'PUBLISHED')
                    """,
                    project,
                    site,
                    "虚构场次" + i);
            sessions[i] =
                    jdbc.queryForObject(
                            "SELECT id FROM exam_session WHERE site_id=?", Long.class, site);
        }
        return sessions;
    }

    // endregion

    // region HTTP 客户端与响应类型

    private Client client() {
        Client client = new Client();
        clients.add(client);
        return client;
    }

    private Client withCookie(String cookie) {
        Client client = client();
        client.forcedCookie = cookie;
        return client;
    }

    private record Result(int status, JsonNode body, HttpResponse<String> response) {}

    private final class Client implements AutoCloseable {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient http =
                HttpClient.newBuilder()
                        .cookieHandler(cookies)
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();
        private String token;
        private String forcedCookie;

        String csrf() throws Exception {
            if (token == null) {
                Result result = send("GET", "/csrf-tokens", null, null);
                assertEquals(200, result.status());
                token = result.body().path("token").asText();
            }
            return token;
        }

        String sessionId() {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> "JSESSIONID".equals(cookie.getName()))
                    .findFirst()
                    .orElseThrow()
                    .getValue();
        }

        Result login(String identifier, String portal) throws Exception {
            Result result =
                    write(
                            "POST",
                            "/web-sessions",
                            Map.of(
                                    "identifier",
                                    identifier,
                                    "password",
                                    PASSWORD,
                                    "portal",
                                    portal));
            token = null;
            return result;
        }

        Result write(String method, String path, Object body) throws Exception {
            return send(method, path, body, csrf());
        }

        Result send(String method, String path, Object body, String csrf) throws Exception {
            var request =
                    HttpRequest.newBuilder(
                                    URI.create("http://127.0.0.1:" + port + "/api/v1" + path))
                            .timeout(Duration.ofSeconds(20))
                            .header("Accept", "application/json");
            if (forcedCookie != null) {
                request.header("Cookie", "JSESSIONID=" + forcedCookie);
            }
            if (csrf != null) {
                request.header("X-CSRF-TOKEN", csrf);
            }
            if (body != null) {
                request.header("Content-Type", "application/json");
            }
            var response =
                    http.send(
                            request.method(
                                            method,
                                            body == null
                                                    ? HttpRequest.BodyPublishers.noBody()
                                                    : HttpRequest.BodyPublishers.ofString(
                                                            json.writeValueAsString(body)))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            JsonNode payload =
                    response.body().isBlank()
                            ? json.createObjectNode()
                            : json.readTree(response.body());
            return new Result(response.statusCode(), payload, response);
        }

        @Override
        public void close() {
            http.close();
        }
    }

    // endregion

    // region 日志追踪、敏感信息与事务一致性

    @Test
    void authenticationLogsCorrelateRequestsAndExcludeCredentialsAndPersonalFields()
            throws Exception {
        Client client = client();
        String identifier = "log.privacy.user";
        String displayName = "虚构日志隐私样本";
        String anonymousToken = client.csrf();
        String oldSession = client.sessionId();
        try (var logs = new LogCapture()) {
            Result registered =
                    client.write(
                            "POST",
                            "/candidate-accounts",
                            Map.of(
                                    "identifier",
                                    identifier,
                                    "password",
                                    PASSWORD,
                                    "displayName",
                                    displayName));
            assertEquals(201, registered.status());
            Result rejected =
                    client.write(
                            "POST",
                            "/web-sessions",
                            Map.of(
                                    "identifier",
                                    identifier,
                                    "password",
                                    "FictionalWrong2026",
                                    "portal",
                                    "CANDIDATE"));
            assertEquals(401, rejected.status());
            Result login = client.login(identifier, "CANDIDATE");
            assertEquals(201, login.status());
            String session = client.sessionId();
            String token = client.csrf();
            Result logout = client.write("DELETE", "/web-sessions/current", null);
            assertEquals(204, logout.status());
            for (var expected :
                    Map.of(
                                    "event=candidate_registered", registered,
                                    "event=request_rejected", rejected,
                                    "event=web_login_succeeded", login,
                                    "event=web_logout_succeeded", logout)
                            .entrySet()) {
                String requestId =
                        expected.getValue()
                                .response()
                                .headers()
                                .firstValue("X-Request-ID")
                                .orElseThrow();
                assertTrue(
                        logs.events().stream()
                                .anyMatch(
                                        event ->
                                                event.getFormattedMessage()
                                                                .contains(expected.getKey())
                                                        && requestId.equals(
                                                                event.getMDCPropertyMap()
                                                                        .get("requestId"))));
            }
            String hash =
                    jdbc.queryForObject(
                            "SELECT password_hash FROM web_credential WHERE identifier=?",
                            String.class,
                            identifier);
            for (var event : logs.events()) {
                for (String sensitive :
                        List.of(
                                identifier,
                                displayName,
                                PASSWORD,
                                "FictionalWrong2026",
                                anonymousToken,
                                oldSession,
                                session,
                                token,
                                hash)) {
                    assertFalse(
                            event.getFormattedMessage().contains(sensitive),
                            "Application log must not contain credentials or personal fields");
                }
            }
        }
    }

    @Test
    void rolledBackAdministratorCreationDoesNotLogACommittedChange() {
        try (var logs = new LogCapture()) {
            new TransactionTemplate(transactions)
                    .executeWithoutResult(
                            status -> {
                                administrators.bootstrap("rollback.log.admin", PASSWORD, "虚构回滚管理员");
                                assertFalse(
                                        logs.events().stream()
                                                .anyMatch(
                                                        event ->
                                                                event.getFormattedMessage()
                                                                        .contains(
                                                                                "event=account_change_committed")));
                                status.setRollbackOnly();
                            });
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Long.class));
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Long.class));
            assertFalse(
                    logs.events().stream()
                            .anyMatch(
                                    event ->
                                            event.getFormattedMessage()
                                                    .contains("event=account_change_committed")));

            long id = administrators.bootstrap("committed.log.admin", PASSWORD, "虚构提交管理员");
            assertTrue(
                    logs.events().stream()
                            .anyMatch(
                                    event ->
                                            event.getFormattedMessage()
                                                            .contains(
                                                                    "event=account_change_committed action=admins.bootstrap")
                                                    && event.getFormattedMessage()
                                                            .contains("targetId=" + id)));
        }
    }

    // endregion

    // region 可控测试时钟

    @TestConfiguration
    static class TimeConfiguration {
        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock();
        }
    }

    static final class TestClock extends Clock {
        private final AtomicLong millis =
                new AtomicLong(Instant.parse("2026-11-20T00:00:00Z").toEpochMilli());

        void advance(Duration duration) {
            millis.addAndGet(duration.toMillis());
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
        }
    }
    // endregion

}
