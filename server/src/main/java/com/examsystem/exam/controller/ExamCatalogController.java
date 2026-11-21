package com.examsystem.exam.controller;

import com.examsystem.common.dto.PageResponse;
import com.examsystem.common.error.ApiErrors;
import com.examsystem.exam.dto.ExamRequests;
import com.examsystem.exam.dto.ExamResponses;
import com.examsystem.exam.service.ExamCatalogService;
import com.examsystem.exam.service.ExamCommandExecutor;
import com.examsystem.exam.service.ExamQueryService;
import com.examsystem.exam.service.ExamVenueService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 基础目录 HTTP 入口；身份从会话取得，PATCH 缺省字段交由事务服务保留。 */
@RestController
@RequestMapping("/api/v1/admin")
public class ExamCatalogController {
    private final ExamCatalogService catalog;
    private final ExamQueryService queries;
    private final ExamVenueService venues;
    private final ExamCommandExecutor commands;

    public ExamCatalogController(
            ExamCatalogService catalog,
            ExamQueryService queries,
            ExamVenueService venues,
            ExamCommandExecutor commands) {
        this.catalog = catalog;
        this.queries = queries;
        this.venues = venues;
        this.commands = commands;
    }

    // region 学校与科目

    @GetMapping("/schools")
    public PageResponse<ExamResponses.School> schools(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String status) {
        return catalog.schools(page, size, keyword, status);
    }

    @PostMapping("/schools")
    public ResponseEntity<Map<String, String>> createSchool(
            @RequestBody ExamRequests.SchoolInput body, HttpServletRequest request) {
        String rid = ApiErrors.requestId(request);
        return created(
                "schools",
                commands.execute(
                        "schools.create",
                        "SCHOOL",
                        null,
                        rid,
                        () -> catalog.saveSchool(null, body, rid)));
    }

    @PatchMapping("/schools/{id}")
    public Map<String, String> updateSchool(
            @PathVariable @Positive long id,
            @RequestBody ExamRequests.SchoolInput body,
            HttpServletRequest request) {
        String rid = ApiErrors.requestId(request);
        return result(
                commands.execute(
                        "schools.update",
                        "SCHOOL",
                        id,
                        rid,
                        () -> catalog.saveSchool(id, body, rid)));
    }

    @GetMapping("/subjects")
    public PageResponse<ExamResponses.Subject> subjects(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String status) {
        return catalog.subjects(page, size, keyword, status);
    }

    @PostMapping("/subjects")
    public ResponseEntity<Map<String, String>> createSubject(
            @RequestBody ExamRequests.SubjectInput body, HttpServletRequest request) {
        String rid = ApiErrors.requestId(request);
        return created(
                "subjects",
                commands.execute(
                        "subjects.create",
                        "SUBJECT",
                        null,
                        rid,
                        () -> catalog.saveSubject(null, body, rid)));
    }

    @PatchMapping("/subjects/{id}")
    public Map<String, String> updateSubject(
            @PathVariable @Positive long id,
            @RequestBody ExamRequests.SubjectInput body,
            HttpServletRequest request) {
        String rid = ApiErrors.requestId(request);
        return result(
                commands.execute(
                        "subjects.update",
                        "SUBJECT",
                        id,
                        rid,
                        () -> catalog.saveSubject(id, body, rid)));
    }

    // endregion

    // region 考点与考场

    @GetMapping("/sites")
    public PageResponse<ExamResponses.Site> sites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String status) {
        return queries.sites(page, size, keyword, status);
    }

    @PostMapping("/sites")
    public ResponseEntity<Map<String, String>> createSite(
            @RequestBody ExamRequests.SiteInput body, HttpServletRequest request) {
        String rid = ApiErrors.requestId(request);
        return created(
                "sites",
                commands.execute(
                        "sites.create", "SITE", null, rid, () -> venues.saveSite(null, body, rid)));
    }

    @PatchMapping("/sites/{id}")
    public Map<String, String> updateSite(
            @PathVariable @Positive long id,
            @RequestBody ExamRequests.SiteInput body,
            HttpServletRequest request) {
        String rid = ApiErrors.requestId(request);
        return result(
                commands.execute(
                        "sites.update", "SITE", id, rid, () -> venues.saveSite(id, body, rid)));
    }

    @GetMapping("/rooms")
    public PageResponse<ExamResponses.Room> rooms(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String status) {
        return queries.rooms(page, size, keyword, status);
    }

    @PostMapping("/rooms")
    public ResponseEntity<Map<String, String>> createRoom(
            @RequestBody ExamRequests.RoomInput body, HttpServletRequest request) {
        String rid = ApiErrors.requestId(request);
        return created(
                "rooms",
                commands.execute(
                        "rooms.create", "ROOM", null, rid, () -> venues.saveRoom(null, body, rid)));
    }

    @PatchMapping("/rooms/{id}")
    public Map<String, String> updateRoom(
            @PathVariable @Positive long id,
            @RequestBody ExamRequests.RoomInput body,
            HttpServletRequest request) {
        String rid = ApiErrors.requestId(request);
        return result(
                commands.execute(
                        "rooms.update", "ROOM", id, rid, () -> venues.saveRoom(id, body, rid)));
    }

    // endregion

    static Map<String, String> result(long id) {
        return Map.of("id", Long.toString(id));
    }

    static ResponseEntity<Map<String, String>> created(String resource, long id) {
        return ResponseEntity.created(URI.create("/api/v1/admin/" + resource + "/" + id))
                .body(result(id));
    }
}
