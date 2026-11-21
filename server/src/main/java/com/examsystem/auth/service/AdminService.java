package com.examsystem.auth.service;

import com.examsystem.audit.service.AuditService;
import com.examsystem.auth.assembler.AdminResponseAssembler;
import com.examsystem.auth.dto.AdminRequests;
import com.examsystem.auth.entity.UserAccount;
import com.examsystem.auth.mapper.AdminMapper;
import com.examsystem.auth.mapper.AuthMapper;
import com.examsystem.auth.mapper.WebCredentialMapper;
import com.examsystem.auth.model.AuthData;
import com.examsystem.auth.validation.AccountRules;
import com.examsystem.common.error.ApiException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 内部账号写入的事务入口：负责加锁、重新判权、协调账号与授权写入、保存成功审计。 */
@Service
public class AdminService {
    // region 依赖与构造

    private final AdminMapper adminMapper;
    private final AuthMapper accountMapper;
    private final WebCredentialMapper credentialMapper;
    private final PermissionService permissionService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final GrantService grantService;

    public AdminService(
            AdminMapper adminMapper,
            AuthMapper accountMapper,
            WebCredentialMapper credentialMapper,
            PermissionService permissionService,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            GrantService grantService) {
        this.adminMapper = adminMapper;
        this.accountMapper = accountMapper;
        this.credentialMapper = credentialMapper;
        this.permissionService = permissionService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.grantService = grantService;
    }

    // endregion

    // region 账号创建与修改

    /** 创建内部账号与逐条授权；任一步失败时凭据、账号、授权和审计一起回滚。 */
    @Transactional
    public long create(AdminRequests.StaffRequest input, String requestId) {
        // 判权先于昂贵的密码哈希；获取锁后再次判权，避免排队期间权限被撤销。
        permissionService.requireSystem("users.write");
        String identifier = AccountRules.identifier(input.identifier());
        String hash = passwordEncoder.encode(AccountRules.password(input.initialPassword()));
        String name = AccountRules.displayName(input.displayName());
        String status = AccountRules.status(input.status() == null ? "ACTIVE" : input.status());
        lockActorAndTarget(null);
        permissionService.requireSystem("users.write");
        grantService.validate(input.grants());

        long userId;
        try {
            userId = insertStaffWithCredential(identifier, hash, name);
        } catch (DuplicateKeyException conflict) {
            throw new ApiException(HttpStatus.CONFLICT, "IDENTIFIER_TAKEN", "此账号已被使用");
        }
        adminMapper.updateStaff(userId, name, status);
        grantService.replace(userId, input.grants());
        auditService.success(
                permissionService.actorId(),
                "users.create",
                userId,
                Map.of("status", status, "grants", input.grants()),
                requestId);
        return userId;
    }

    /** null 授权表示不修改，空数组表示撤销全部；禁止修改自己或撤销最后的管理入口。 */
    @Transactional
    public void update(long target, AdminRequests.StaffUpdate input, String requestId) {
        lockActorAndTarget(target);
        permissionService.requireSystem("users.write");
        if (target == permissionService.actorId()) {
            throw ApiException.forbidden();
        }
        AuthData.User user = accountMapper.user(target);
        if (user == null || !"STAFF".equals(user.accountKind())) {
            throw ApiException.notFound();
        }
        var before = accountMapper.actions(target);
        if (before.stream().anyMatch(grant -> "SYSTEM_ADMIN".equals(grant.roleCode()))) {
            permissionService.requireSystem("admins.manage");
        }
        if (input.grants() != null) {
            grantService.validate(input.grants());
        }
        String name =
                input.displayName() == null
                        ? user.displayName()
                        : AccountRules.displayName(input.displayName());
        String status =
                input.status() == null ? user.status() : AccountRules.status(input.status());
        adminMapper.updateStaff(target, name, status);
        if (input.grants() != null) {
            grantService.replace(target, input.grants());
        }
        requireControllingAdministrator();

        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("statusBefore", user.status());
        changes.put("statusAfter", status);
        changes.put("grantsBefore", AdminResponseAssembler.grants(before));
        changes.put("grantsAfter", AdminResponseAssembler.grants(accountMapper.actions(target)));
        auditService.success(
                permissionService.actorId(), "users.update", target, changes, requestId);
    }

    // endregion

    // region 首位管理员的受控初始化

    /** 仅供交互命令调用；历史上存在过管理员授权时也拒绝重新初始化。 */
    @Transactional
    public long bootstrap(String identifier, String password, String name) {
        String normalized = AccountRules.identifier(identifier);
        String hash = passwordEncoder.encode(AccountRules.password(password));
        String displayName = AccountRules.displayName(name);
        if (adminMapper.lockAdministratorRole() == null
                || adminMapper.administratorGrantCount() != 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "STATE_CONFLICT", "权限字典未初始化或管理员已存在，不能重复初始化");
        }
        long userId = insertStaffWithCredential(normalized, hash, displayName);
        adminMapper.upsertGrant(userId, userId, "SYSTEM_ADMIN", "SYSTEM", null, null);
        long grantId = adminMapper.grantId(userId, "SYSTEM_ADMIN", "SYSTEM", 0);
        // 初始化是唯一可授予 admins.manage 的入口，不复用 HTTP 委派规则。
        for (var ceiling : adminMapper.ceilings("SYSTEM_ADMIN")) {
            adminMapper.grantPermission(
                    grantId, "SYSTEM_ADMIN", "SYSTEM", ceiling.permissionCode());
        }
        auditService.success(
                userId,
                "admins.bootstrap",
                userId,
                Map.of("scope", "SYSTEM"),
                "bootstrap-" + UUID.randomUUID());
        return userId;
    }

    // endregion

    // region 事务内部的锁定、持久化与入口保护

    private long insertStaffWithCredential(String identifier, String hash, String displayName) {
        UserAccount user = new UserAccount();
        user.setAccountKind("STAFF");
        user.setDisplayName(displayName);
        accountMapper.insert(user);
        credentialMapper.create(user.getId(), identifier, hash);
        return user.getId();
    }

    private void lockActorAndTarget(Long target) {
        // 所有账号/授权变更先锁固定角色行，再按 ID 升序锁账号，避免管理员互改死锁。
        adminMapper.lockAdministratorRole();
        AuthData.Principal principal = permissionService.principal();
        long actor = principal.userId();
        if (target != null && target < actor) {
            accountMapper.lockUser(target);
        }
        AuthData.User current = accountMapper.lockUser(actor);
        if (target != null && target > actor) {
            accountMapper.lockUser(target);
        }
        if (current == null
                || !"ACTIVE".equals(current.status())
                || current.sessionVersion() != principal.sessionVersion()) {
            throw AuthService.invalidCredentials();
        }
    }

    private void requireControllingAdministrator() {
        // 和修改处于同一事务、同一角色行锁下，确保并发撤权后仍有可管理授权的账号。
        if (adminMapper.controllingAdministratorCount() == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "须保留至少一位可管理授权的启用管理员");
        }
    }

    // endregion
}
