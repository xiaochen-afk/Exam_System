package com.examsystem.audit.service;

import com.examsystem.audit.entity.AuditEvent;
import com.examsystem.audit.mapper.AuditMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

/** 账号审计的事务边界与提交后事件日志；成功随业务提交，失败在业务回滚后独立保存。 */
@Service
public class AuditService {
    // region 依赖与构造

    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);
    private final AuditMapper mapper;
    private final ObjectMapper json;

    public AuditService(AuditMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    // endregion

    // region 审计写入

    /** 只接受已筛选的变更摘要；无外部业务事务时拒绝调用，不能记录未提交的成功。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void success(
            long actor, String action, long target, Map<String, Object> changes, String requestId) {
        mapper.insert(
                actor,
                "内部账号#" + actor,
                action,
                target,
                json.writeValueAsString(changes),
                "SUCCESS",
                requestId);
        logAfterCommit(actor, action, target, null, requestId);
    }

    /** Controller 在业务事务已回滚后调用；独立事务确保失败原因仍可追查。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(long actor, String action, Long target, String code, String requestId) {
        mapper.insert(
                actor,
                "内部账号#" + actor,
                action,
                target,
                json.writeValueAsString(Map.of("failureCode", code)),
                "FAILED",
                requestId);
        logAfterCommit(actor, action, target, code, requestId);
    }

    // endregion

    /** 考务资源审计复用现有事务与表；调用方只提交字段白名单，不保存请求体。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void resourceSuccess(
            long actor,
            String action,
            String targetType,
            long target,
            String scope,
            Map<String, Object> changes,
            String requestId) {
        resourceEvent(actor, action, targetType, target, scope, changes, "SUCCESS", requestId);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        LOG.info(
                                "event=resource_change_committed action={} actorId={} targetType={} targetId={} requestId={}",
                                action,
                                actor,
                                targetType,
                                target,
                                requestId);
                    }
                });
    }

    /** 控制器在业务回滚后记录受控拒绝代码，不持久化异常消息。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resourceFailure(
            long actor,
            String action,
            String targetType,
            Long target,
            String code,
            String requestId) {
        resourceEvent(
                actor,
                action,
                targetType,
                target,
                null,
                Map.of("failureCode", code),
                "FAILED",
                requestId);
    }

    private void resourceEvent(
            long actor,
            String action,
            String type,
            Long target,
            String scope,
            Map<String, Object> changes,
            String result,
            String requestId) {
        AuditEvent event = new AuditEvent();
        event.setActorId(actor);
        event.setActorLabel("内部账号#" + actor);
        event.setActionCode(action);
        event.setTargetType(type);
        event.setTargetId(target);
        event.setScopeLabel(scope);
        event.setSummary(json.writeValueAsString(changes));
        event.setResult(result);
        event.setRequestId(requestId);
        mapper.insert(event);
    }

    // region 提交后日志

    private void logAfterCommit(
            long actor, String action, Long target, String code, String requestId) {
        // 账号变更与审计共用事务，回滚时不能留下“变更成功”的运行日志。
        // 不输出 changes JSON，其中可能包含后续业务的敏感字段。
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        if (code == null) {
                            LOG.info(
                                    "event=account_change_committed action={} actorId={} targetId={} requestId={}",
                                    action,
                                    actor,
                                    target,
                                    requestId);
                        } else {
                            LOG.warn(
                                    "event=account_change_rejected action={} actorId={} targetId={} code={} requestId={}",
                                    action,
                                    actor,
                                    target,
                                    code,
                                    requestId);
                        }
                    }
                });
    }

    // endregion
}
