package com.examsystem.support;

import com.examsystem.ExamSystemServerApplication;
import com.examsystem.auth.dto.AdminRequests;
import com.examsystem.auth.mapper.AuthMapper;
import com.examsystem.auth.model.AuthData;
import com.examsystem.auth.service.AdminService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** 测试类路径中的真实 Web 联调入口：仅绑定本机，使用随机隔离库，不加载到正式 JAR。 */
public final class FrontendIntegrationApplication extends MySqlIntegrationSupport {
    private static final String PASSWORD = "WebIntegration2026";

    private FrontendIntegrationApplication() {}

    public static void main(String[] args) throws Exception {
        try (var context =
                SpringApplication.run(
                        ExamSystemServerApplication.class,
                        "--server.address=127.0.0.1",
                        "--server.port=18080",
                        "--spring.datasource.url=" + isolatedDatabaseUrl())) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            long session = seedSessions(jdbc);
            AdminService administrators = context.getBean(AdminService.class);
            long admin = administrators.bootstrap("qa.admin", PASSWORD, "虚构联调管理员");
            AuthData.User user = context.getBean(AuthMapper.class).user(admin);
            SecurityContextHolder.getContext()
                    .setAuthentication(
                            UsernamePasswordAuthenticationToken.authenticated(
                                    new AuthData.Principal(admin, user.sessionVersion()),
                                    null,
                                    List.of()));
            try {
                administrators.create(
                        new AdminRequests.StaffRequest(
                                "qa.site",
                                PASSWORD,
                                "虚构甲考点人员",
                                "ACTIVE",
                                List.of(
                                        new AdminRequests.GrantRequest(
                                                "SITE_MANAGER",
                                                "SESSION",
                                                null,
                                                session,
                                                List.of("sessions.read")))),
                        "frontend-integration-seed");
            } finally {
                SecurityContextHolder.clearContext();
            }
            System.out.println(
                    "FRONTEND_INTEGRATION_READY port=18080; enter stop to cleanly shut down.");
            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                String command = input.readLine();
                if (command == null || command.equals("stop")) break;
            }
        }
        // 正常退出触发父类的删除隔离库 hook；不修改正式库和系统 MySQL 服务。
    }

    private static long seedSessions(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO exam_subject(code,name) VALUES('QA-SUBJECT','虚构联调科目')");
        long subject =
                jdbc.queryForObject(
                        "SELECT id FROM exam_subject WHERE code='QA-SUBJECT'", Long.class);
        jdbc.update(
                "INSERT INTO exam_project(subject_id,application_code) VALUES(?,'QA-PROJECT')",
                subject);
        long project =
                jdbc.queryForObject(
                        "SELECT id FROM exam_project WHERE application_code='QA-PROJECT'",
                        Long.class);
        long first = 0;
        for (String suffix : List.of("甲", "乙")) {
            String siteName = "虚构联调考点" + suffix;
            jdbc.update(
                    "INSERT INTO exam_site(name,address,qualification_status) VALUES(?,'虚构联调地址','QUALIFIED')",
                    siteName);
            long site =
                    jdbc.queryForObject(
                            "SELECT id FROM exam_site WHERE name=?", Long.class, siteName);
            jdbc.update(
                    "INSERT INTO exam_project_site(project_id,site_id,registration_open) VALUES(?,?,1)",
                    project,
                    site);
            jdbc.update(
                    """
                    INSERT INTO exam_session(project_id,site_id,name,registration_starts_at,registration_ends_at,
                    exam_starts_at,exam_ends_at,capacity,status)
                    VALUES(?, ?, ?, '2026-11-01 00:00:00', '2026-11-30 00:00:00',
                    '2026-12-10 01:00:00', '2026-12-10 03:00:00', 20, 'PUBLISHED')
                    """,
                    project,
                    site,
                    "联调场次" + suffix);
            long id =
                    jdbc.queryForObject(
                            "SELECT id FROM exam_session WHERE site_id=?", Long.class, site);
            if (first == 0) first = id;
        }
        return first;
    }
}
