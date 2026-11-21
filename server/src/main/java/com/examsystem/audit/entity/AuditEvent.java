package com.examsystem.audit.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 审计写入实体；summary 只保存白名单摘要，不生成包含字段内容的 toString。 */
@Getter
@Setter
@TableName("audit_event")
public class AuditEvent {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long actorId;
    private String actorLabel;
    private String actionCode;
    private String targetType;
    private Long targetId;
    private String scopeLabel;

    @TableField(select = false)
    private String summary;

    private String result;
    private String requestId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime occurredAt;
}
