package com.examsystem;

import com.examsystem.auth.bootstrap.AdminBootstrapCommand;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 应用入口；显式初始化参数进入无 HTTP 的管理员交互流程，其余情况启动 Web 服务。 */
@SpringBootApplication(
        excludeName =
                "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration")
public class ExamSystemServerApplication {

    public static void main(String[] args) {
        if (Arrays.asList(args).contains("--app.bootstrap-admin=true")) {
            System.exit(AdminBootstrapCommand.run(args));
            return;
        }
        SpringApplication.run(ExamSystemServerApplication.class, args);
    }
}
