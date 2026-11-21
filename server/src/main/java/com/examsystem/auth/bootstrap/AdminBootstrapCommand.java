package com.examsystem.auth.bootstrap;

import com.examsystem.ExamSystemServerApplication;
import com.examsystem.auth.service.AdminService;
import com.examsystem.common.error.ApiException;
import java.io.Console;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

/** 首位管理员的交互终端入口；密码不经命令参数或日志传递，初始化结束后清除字符数组。 */
public final class AdminBootstrapCommand {
    private static final Logger LOG = LoggerFactory.getLogger(AdminBootstrapCommand.class);

    private AdminBootstrapCommand() {}

    public static int run(String[] args) {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("请在交互终端使用 java -jar 执行初始化，不支持 HTTP 或无控制台运行");
        }
        String identifier = console.readLine("Administrator identifier: ");
        String name = console.readLine("Display name: ");
        char[] password = console.readPassword("Password (not echoed): ");
        char[] confirmation = console.readPassword("Confirm password: ");
        try {
            if (password == null
                    || confirmation == null
                    || !Arrays.equals(password, confirmation)) {
                throw ApiException.invalid("两次密码不一致");
            }
            SpringApplication application =
                    new SpringApplication(ExamSystemServerApplication.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            try (var context = application.run(args)) {
                long id =
                        context.getBean(AdminService.class)
                                .bootstrap(identifier, new String(password), name);
                console.printf("Administrator initialized. ID: %d%n", id);
            }
            return 0;
        } catch (ApiException error) {
            LOG.warn("event=admin_bootstrap_rejected code={}", error.code());
            console.printf("Initialization rejected: %s%n", error.getMessage());
            return 1;
        } catch (RuntimeException error) {
            LOG.error("event=admin_bootstrap_failed exceptionType={}", error.getClass().getName());
            console.printf(
                    "Initialization failed (%s). Check database availability and existing identifiers.%n",
                    error.getClass().getSimpleName());
            return 1;
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
            if (confirmation != null) {
                Arrays.fill(confirmation, '\0');
            }
        }
    }
}
