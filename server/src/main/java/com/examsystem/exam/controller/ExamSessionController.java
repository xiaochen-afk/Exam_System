package com.examsystem.exam.controller;

import com.examsystem.common.dto.PageResponse;
import com.examsystem.common.error.ApiErrors;
import com.examsystem.exam.dto.ExamRequests;
import com.examsystem.exam.dto.ExamResponses;
import com.examsystem.exam.service.ExamCommandExecutor;
import com.examsystem.exam.service.ExamQueryService;
import com.examsystem.exam.service.ExamSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 项目建立、考点报考开关及场次写入入口；列表仍使用既有后台授权场次读取。 */
@RestController
@RequestMapping("/api/v1/admin")
public class ExamSessionController {
    private final ExamQueryService queries;
    private final ExamSessionService sessions;
    private final ExamCommandExecutor commands;

    public ExamSessionController(
            ExamQueryService queries, ExamSessionService sessions, ExamCommandExecutor commands) {
        this.queries = queries;
        this.sessions = sessions;
        this.commands = commands;
    }

    @GetMapping("/exam-projects")
    public PageResponse<ExamResponses.Project> projects(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword) {
        return queries.projects(page, size, keyword);
    }

    @PostMapping("/exam-projects")
    public ResponseEntity<Map<String, String>> createProject(
            @RequestBody ExamRequests.ProjectInput body, HttpServletRequest request) {
        String rid = ApiErrors.requestId(request);
        return ExamCatalogController.created(
                "exam-projects",
                commands.execute(
                        "projects.create",
                        "PROJECT",
                        null,
                        rid,
                        () -> sessions.createProject(body, rid)));
    }

    @PutMapping("/exam-projects/{projectId}/sites/{siteId}")
    public ResponseEntity<Void> registrationOpen(
            @PathVariable @Positive long projectId,
            @PathVariable @Positive long siteId,
            @RequestBody ExamRequests.ProjectSiteInput body,
            HttpServletRequest request) {
        String rid = ApiErrors.requestId(request);
        commands.execute(
                "project-sites.update",
                "PROJECT_SITE",
                siteId,
                rid,
                () -> {
                    sessions.setRegistrationOpen(projectId, siteId, body, rid);
                    return siteId;
                });
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/exam-sessions")
    public ResponseEntity<Map<String, String>> createSession(
            @RequestBody ExamRequests.SessionInput body, HttpServletRequest request) {
        String rid = ApiErrors.requestId(request);
        return ExamCatalogController.created(
                "exam-sessions",
                commands.execute(
                        "sessions.create",
                        "SESSION",
                        null,
                        rid,
                        () -> sessions.createSession(body, rid)));
    }

    @PatchMapping("/exam-sessions/{id}")
    public Map<String, String> updateSession(
            @PathVariable @Positive long id,
            @RequestBody ExamRequests.SessionInput body,
            HttpServletRequest request) {
        String rid = ApiErrors.requestId(request);
        return ExamCatalogController.result(
                commands.execute(
                        body.status() == null ? "sessions.update" : "sessions.publish",
                        "SESSION",
                        id,
                        rid,
                        () -> sessions.updateSession(id, body, rid)));
    }
}
