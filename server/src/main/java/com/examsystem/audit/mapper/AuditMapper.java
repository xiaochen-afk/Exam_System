package com.examsystem.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.examsystem.audit.entity.AuditEvent;
import org.apache.ibatis.annotations.Mapper;

/** 审计写入复用 MyBatis-Plus；成功与失败的事务传播由 AuditService 管理。 */
@Mapper
public interface AuditMapper extends BaseMapper<AuditEvent> {
    /** 当前仅记录内部账号 SYSTEM / STAFF 审计，summary 须为已筛选的白名单摘要。 */
    default void insert(
            Long actor,
            String label,
            String action,
            Long target,
            String summary,
            String result,
            String requestId) {
        AuditEvent event = new AuditEvent();
        event.setActorId(actor);
        event.setActorLabel(label);
        event.setActionCode(action);
        event.setTargetType("STAFF");
        event.setTargetId(target);
        event.setScopeLabel("SYSTEM");
        event.setSummary(summary);
        event.setResult(result);
        event.setRequestId(requestId);
        insert(event);
    }
}
