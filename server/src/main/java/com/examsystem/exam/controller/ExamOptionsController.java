package com.examsystem.exam.controller;

import com.examsystem.exam.dto.ExamResponses;
import com.examsystem.exam.service.ExamQueryService;
import com.examsystem.registration.service.AdminRegistrationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 统一表单选项路由；明确分发已实现上下文，未知业务不开放通用目录。 */
@RestController
@RequestMapping("/api/v1/admin")
public class ExamOptionsController {
    private final ExamQueryService exams;
    private final AdminRegistrationService registrations;

    public ExamOptionsController(ExamQueryService exams, AdminRegistrationService registrations) {
        this.exams = exams;
        this.registrations = registrations;
    }

    @GetMapping("/options")
    public List<ExamResponses.Option> options(
            @RequestParam String context, @RequestParam String kind) {
        if ("registrations".equals(context))
            return registrations.options(context, kind).stream()
                    .map(row -> new ExamResponses.Option(row.id(), row.name()))
                    .toList();
        return exams.options(context, kind);
    }
}
