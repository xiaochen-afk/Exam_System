package com.examsystem.registration.service;

import com.examsystem.auth.mapper.AuthMapper;
import com.examsystem.auth.service.PermissionService;
import com.examsystem.common.error.ApiException;
import com.examsystem.common.query.PageQuery;
import com.examsystem.registration.assembler.RegistrationResponseAssembler;
import com.examsystem.registration.dto.RegistrationRequests;
import com.examsystem.registration.dto.RegistrationResponses;
import com.examsystem.registration.mapper.RegistrationReadMapper;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 后台报名只读查询；入口动作与逐条范围都验证，管理员身份或考生在读学校不扩大范围。 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AdminRegistrationService {
    private final PermissionService permissions;
    private final AuthMapper accounts;
    private final RegistrationReadMapper reads;

    public AdminRegistrationService(
            PermissionService permissions, AuthMapper accounts, RegistrationReadMapper reads) {
        this.permissions = permissions;
        this.accounts = accounts;
        this.reads = reads;
    }

    /** 使用同一只读事务快照读取页面与摘要；total 复用摘要，避免第三份筛选逻辑。 */
    public RegistrationResponses.AdminPage list(
            int page, int size, RegistrationRequests.AdminFilter filter) {
        long actor = requireReader();
        PageQuery query = new PageQuery(page, size, filter.registrationNumber());
        if (filter.sessionId() != null && filter.sessionId() < 1
                || filter.siteId() != null && filter.siteId() < 1) {
            throw ApiException.invalid("场次与考点 ID 必须为正数");
        }
        var criteria =
                new RegistrationReadMapper.AdminQuery(
                        filter.sessionId(),
                        filter.siteId(),
                        filter.candidateType() == null ? "" : filter.candidateType().name(),
                        filter.status() == null ? "" : filter.status().name(),
                        query.keyword());
        var items =
                reads.adminList(actor, criteria, query.size(), query.offset()).stream()
                        .map(RegistrationResponseAssembler::admin)
                        .toList();
        var summary = reads.summary(actor, criteria);
        return new RegistrationResponses.AdminPage(
                items, query.page(), query.size(), summary.total(), summary);
    }

    public RegistrationResponses.AdminItem detail(long id) {
        var row = reads.adminDetail(requireReader(), id);
        if (row == null) throw ApiException.notFound();
        return RegistrationResponseAssembler.admin(row);
    }

    /** 当前仅实现报名上下文；筛选项从授权场次推导，不借用全局考点/角色接口。 */
    public List<RegistrationResponses.NamedOption> options(String context, String kind) {
        long actor = requireReader();
        if (!"registrations".equals(context)) throw ApiException.invalid("该筛选上下文尚未支持");
        return switch (kind) {
            case "sessions" -> reads.sessionOptions(actor);
            case "sites" -> reads.siteOptions(actor);
            default -> throw ApiException.invalid("报名筛选项只支持 sessions 或 sites");
        };
    }

    private long requireReader() {
        var principal = permissions.principal();
        var user = accounts.user(principal.userId());
        if (user == null
                || !"ACTIVE".equals(user.status())
                || user.sessionVersion() != principal.sessionVersion()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_REQUIRED", "登录已失效，请重新登录");
        }
        if (!"STAFF".equals(user.accountKind())
                || accounts.actions(user.id()).stream()
                        .noneMatch(grant -> "registrations.read".equals(grant.permissionCode()))) {
            throw ApiException.forbidden();
        }
        return user.id();
    }
}
