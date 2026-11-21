package com.examsystem.auth.controller;

import com.examsystem.audit.service.AuditService;
import com.examsystem.auth.dto.AdminRequests;
import com.examsystem.auth.dto.AdminResponses;
import com.examsystem.auth.service.AdminDirectoryService;
import com.examsystem.auth.service.AdminService;
import com.examsystem.auth.service.PermissionService;
import com.examsystem.common.dto.PageResponse;
import com.examsystem.common.error.ApiErrors;
import com.examsystem.common.error.ApiException;
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

/** 内部账号与角色 HTTP 入口；查询交给目录服务，写入交给事务服务，失败审计在回滚后记录。 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    // region 依赖与构造

    private final AdminService service;
    private final AdminDirectoryService directory;
    private final PermissionService permissions;
    private final AuditService audit;

    public AdminController(
            AdminService service,
            AdminDirectoryService directory,
            PermissionService permissions,
            AuditService audit) {
        this.service = service;
        this.directory = directory;
        this.permissions = permissions;
        this.audit = audit;
    }

    // endregion

    // region 查询接口：内部账号与角色

    /** 分页查看内部账号，需系统范围 users.read；筛选与可编辑提示由目录服务生成。 */
    @GetMapping("/users")
    PageResponse<AdminResponses.Staff> users(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return directory.staff(keyword, status, page, size);
    }

    /** 读取角色及可授予动作字典，需系统范围 roles.read。 */
    @GetMapping("/roles")
    PageResponse<AdminResponses.Role> roles(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return directory.roles(page, size);
    }

    // endregion

    // region 写入接口：账号与授权变更

    /** 授权表单的角色/项目/场次基础选项；不能据此读取考试业务记录。 */
    @GetMapping("/grant-options")
    AdminResponses.GrantOptions grantOptions() {
        return directory.grantOptions();
    }

    /** 原子创建内部账号与逐条授权，返回新 ID；拒绝时独立记录失败审计。 */
    @PostMapping("/users")
    ResponseEntity<Map<String, String>> create(
            @RequestBody AdminRequests.StaffRequest body, HttpServletRequest request) {
        try {
            long id = service.create(body, ApiErrors.requestId(request));
            return ResponseEntity.created(URI.create("/api/v1/admin/users/" + id))
                    .body(Map.of("id", Long.toString(id)));
        } catch (ApiException rejected) {
            audit.failure(
                    permissions.actorId(),
                    "users.create",
                    null,
                    rejected.code(),
                    ApiErrors.requestId(request));
            throw rejected;
        }
    }

    /** 修改账号资料/状态/授权；grants 缺省不修改，空数组撤销全部，操作者取自会话。 */
    @PatchMapping("/users/{id}")
    ResponseEntity<Void> update(
            @PathVariable @Positive long id,
            @RequestBody AdminRequests.StaffUpdate body,
            HttpServletRequest request) {
        try {
            service.update(id, body, ApiErrors.requestId(request));
            return ResponseEntity.noContent().build();
        } catch (ApiException rejected) {
            audit.failure(
                    permissions.actorId(),
                    "users.update",
                    id,
                    rejected.code(),
                    ApiErrors.requestId(request));
            throw rejected;
        }
    }
    // endregion

}
