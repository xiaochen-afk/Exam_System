package com.examsystem.auth.service;

import com.examsystem.auth.assembler.AdminResponseAssembler;
import com.examsystem.auth.dto.AdminResponses;
import com.examsystem.auth.mapper.AdminMapper;
import com.examsystem.auth.mapper.AdminReadMapper;
import com.examsystem.auth.mapper.AuthMapper;
import com.examsystem.auth.model.AuthData;
import com.examsystem.common.dto.PageResponse;
import com.examsystem.common.error.ApiException;
import com.examsystem.common.query.PageQuery;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 后台业务读取：先判断入口动作，再由 SQL 对条目、总数和统计应用相同授权范围。 */
@Service
public class AdminReadService {
    // region 依赖与构造

    private final PermissionService permissions;
    private final AuthMapper accounts;
    private final AdminReadMapper mapper;
    private final AdminMapper admin;
    private final StaffAccessService examAccess;

    public AdminReadService(
            PermissionService permissions,
            AuthMapper accounts,
            AdminReadMapper mapper,
            AdminMapper admin,
            StaffAccessService examAccess) {
        this.permissions = permissions;
        this.accounts = accounts;
        this.mapper = mapper;
        this.admin = admin;
        this.examAccess = examAccess;
    }

    // endregion

    // region 工作台统计

    /** 只返回当前内部账号具备动作的指标，统计数值仍由 SQL 限制实际项目/场次范围。 */
    public AdminResponses.Dashboard dashboard() {
        long actor = permissions.actorId();
        AuthData.User user = accounts.user(actor);
        if (user == null || !"STAFF".equals(user.accountKind())) {
            throw ApiException.forbidden();
        }
        var actions =
                accounts.actions(actor).stream().map(AuthData.GrantAction::permissionCode).toList();
        List<AdminResponses.Metric> metrics = new ArrayList<>();
        if (actions.contains("sessions.read")) {
            metrics.add(new AdminResponses.Metric("sessions", mapper.sessionCount(actor, "", "")));
        }
        if (actions.contains("registrations.read")) {
            metrics.add(
                    new AdminResponses.Metric(
                            "registrations",
                            mapper.businessCount(actor, "registrations.read", false)));
        }
        if (actions.contains("arrangements.read")) {
            metrics.add(
                    new AdminResponses.Metric(
                            "assigned", mapper.businessCount(actor, "arrangements.read", true)));
        }
        if (permissions.hasSystem("users.read")) {
            metrics.add(new AdminResponses.Metric("users", admin.staffCount("", "")));
        }
        return new AdminResponses.Dashboard(metrics);
    }

    // endregion

    // region 授权列表

    /** 场次容量仅出现在内部响应；公开及考生接口不能复用该响应类型。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<AdminResponses.Session> sessions(
            int page, int size, String keyword, String status) {
        var actor = examAccess.reader();
        actor.requireAction("sessions.read");
        if (!List.of("", "DRAFT", "PUBLISHED", "COMPLETED").contains(status)) {
            throw ApiException.invalid("场次状态不正确");
        }
        PageQuery query = new PageQuery(page, size, keyword);
        var items =
                mapper
                        .sessions(actor.id(), query.keyword(), status, query.size(), query.offset())
                        .stream()
                        .map(
                                row ->
                                        AdminResponseAssembler.session(
                                                row, sessionCapabilities(actor, row)))
                        .toList();
        return query.response(items, mapper.sessionCount(actor.id(), query.keyword(), status));
    }

    private static List<String> sessionCapabilities(
            StaffAccessService.Actor actor, AdminReadMapper.SessionRow row) {
        List<String> result = new ArrayList<>();
        if (!row.locked() && !"COMPLETED".equals(row.status())) {
            if (actor.session("sessions.write", row.id(), row.projectId())) result.add("EDIT");
            if ("DRAFT".equals(row.status())
                    && actor.session("sessions.publish", row.id(), row.projectId()))
                result.add("PUBLISH");
        }
        if (actor.project("sessions.publish", row.projectId())) result.add("REGISTRATION_OPEN");
        return result;
    }

    /** 系统范围 logs.read 才能查看；SQL 投影排除原始摘要及个人资料。 */
    public PageResponse<AdminResponses.Audit> audit(int page, int size, String keyword) {
        permissions.requireSystem("logs.read");
        PageQuery query = new PageQuery(page, size, keyword);
        var items =
                mapper.audit(query.keyword(), query.size(), query.offset()).stream()
                        .map(AdminResponseAssembler::audit)
                        .toList();
        return query.response(items, mapper.auditCount(query.keyword()));
    }

    // endregion
}
