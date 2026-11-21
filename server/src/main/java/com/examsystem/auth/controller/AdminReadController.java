package com.examsystem.auth.controller;

import com.examsystem.auth.dto.AdminResponses;
import com.examsystem.auth.service.AdminReadService;
import com.examsystem.common.dto.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 后台只读 HTTP 入口；所有范围过滤、动作校验和响应字段选择由服务层承担。 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminReadController {
    private final AdminReadService service;

    public AdminReadController(AdminReadService service) {
        this.service = service;
    }

    /** 只返回当前内部账号获准查看的工作台指标。 */
    @GetMapping("/dashboard")
    AdminResponses.Dashboard dashboard() {
        return service.dashboard();
    }

    /** 按当前授权过滤场次条目和总数，不接受调用方指定操作者或授权范围。 */
    @GetMapping("/exam-sessions")
    PageResponse<AdminResponses.Session> sessions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String status) {
        return service.sessions(page, size, keyword, status);
    }

    /** 分页返回已筛选的审计概览，不返回变更 JSON 明细或原始请求。 */
    @GetMapping("/audit-logs")
    PageResponse<AdminResponses.Audit> audit(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String keyword) {
        return service.audit(page, size, keyword);
    }
}
