package com.examsystem.exam.service;

import com.examsystem.audit.service.AuditService;
import com.examsystem.auth.mapper.AuthMapper;
import com.examsystem.auth.service.PermissionService;
import com.examsystem.common.error.ApiException;
import com.examsystem.exam.validation.ExamRules;
import java.util.function.LongSupplier;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Component;

/** HTTP 命令适配器；业务服务事务回滚后再保存失败审计，统一处理锁等待冲突。 */
@Component
public class ExamCommandExecutor {
    private final AuditService audit;
    private final PermissionService permissions;
    private final AuthMapper accounts;

    public ExamCommandExecutor(
            AuditService audit, PermissionService permissions, AuthMapper accounts) {
        this.audit = audit;
        this.permissions = permissions;
        this.accounts = accounts;
    }

    public long execute(
            String action, String type, Long target, String requestId, LongSupplier command) {
        try {
            return command.getAsLong();
        } catch (PessimisticLockingFailureException | QueryTimeoutException busy) {
            ApiException rejected = ExamRules.conflict("基础信息正在被其他操作占用，请稍后重试");
            failure(action, type, target, requestId, rejected);
            throw rejected;
        } catch (ApiException rejected) {
            failure(action, type, target, requestId, rejected);
            throw rejected;
        }
    }

    private void failure(
            String action, String type, Long target, String requestId, ApiException rejected) {
        long actor = permissions.actorId();
        var user = accounts.user(actor);
        if (user != null && "STAFF".equals(user.accountKind()))
            audit.resourceFailure(actor, action, type, target, rejected.code(), requestId);
    }
}
