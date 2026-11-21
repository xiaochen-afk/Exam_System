package com.examsystem.support;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class MySqlIntegrationSupport {
    private static final String DATABASE =
            "exam_system_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final Properties CONFIG = loadConfiguration();
    private static final URI SOURCE =
            URI.create(CONFIG.getProperty("spring.datasource.url").substring(5));
    private static final String ROOT_URL =
            "jdbc:mysql://" + SOURCE.getRawAuthority() + "/?" + SOURCE.getRawQuery();
    private static final String TEST_URL =
            "jdbc:mysql://"
                    + SOURCE.getRawAuthority()
                    + "/"
                    + DATABASE
                    + "?"
                    + SOURCE.getRawQuery();

    static {
        if (!List.of("localhost", "127.0.0.1", "::1").contains(SOURCE.getHost())
                || !DATABASE.matches("exam_system_test_[a-f0-9]{32}")) {
            throw new IllegalStateException("自动测试仅允许使用本机随机隔离数据库");
        }
        try (Connection connection = connect(ROOT_URL)) {
            connection
                    .createStatement()
                    .execute(
                            "CREATE DATABASE `"
                                    + DATABASE
                                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(
                                    () -> {
                                        try (Connection cleanup = connect(ROOT_URL)) {
                                            cleanup.createStatement()
                                                    .execute("DROP DATABASE `" + DATABASE + "`");
                                            System.out.println(
                                                    "Removed isolated MySQL test database.");
                                        } catch (Exception error) {
                                            System.err.println(
                                                    "Test database cleanup required: " + DATABASE);
                                        }
                                    },
                                    "mysql-test-cleanup"));
            try (Connection isolated = connect(TEST_URL)) {
                ScriptUtils.executeSqlScript(
                        isolated, new ClassPathResource("db/database-schema.sql"));
                ScriptUtils.executeSqlScript(
                        isolated, new ClassPathResource("db/database-role-seed.sql"));
            }
        } catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TEST_URL);
        registry.add(
                "spring.datasource.username",
                () -> CONFIG.getProperty("spring.datasource.username"));
        registry.add(
                "spring.datasource.password",
                () -> CONFIG.getProperty("spring.datasource.password"));
    }

    /** Browser integration launchers share the same guarded, disposable schema setup. */
    protected static String isolatedDatabaseUrl() {
        return TEST_URL;
    }

    protected static void clearBusinessData() throws Exception {
        String schema =
                new ClassPathResource("db/database-schema.sql")
                        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        var matcher = Pattern.compile("CREATE TABLE ([a-z_]+)").matcher(schema);
        List<String> tables = new ArrayList<>();
        while (matcher.find()) {
            if (!List.of("auth_role", "auth_permission", "auth_role_permission")
                    .contains(matcher.group(1))) {
                tables.add(matcher.group(1));
            }
        }
        Collections.reverse(tables);
        try (Connection connection = connect(TEST_URL)) {
            for (String table : tables) {
                connection.createStatement().executeUpdate("DELETE FROM " + table);
            }
        }
    }

    private static Connection connect(String url) throws Exception {
        return DriverManager.getConnection(
                url,
                CONFIG.getProperty("spring.datasource.username"),
                CONFIG.getProperty("spring.datasource.password"));
    }

    private static Properties loadConfiguration() {
        Properties config = new Properties();
        try (var stream = new ClassPathResource("application.properties").getInputStream()) {
            config.load(stream);
            return config;
        } catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }
}
