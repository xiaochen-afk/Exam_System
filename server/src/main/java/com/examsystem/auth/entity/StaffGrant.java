package com.examsystem.auth.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 单条内部授权实体；撤销/恢复仍走带锁的业务事务，不使用通用逻辑删除代替历史记录。 */
@Getter
@Setter
@TableName("staff_grant")
public class StaffGrant {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long staffId;
    private String accountKind;
    private String roleCode;
    private String scopeType;
    private Long projectId;
    private Long sessionId;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Long scopeKey;

    private Long grantedBy;
    private LocalDateTime grantedAt;
    private Long revokedBy;
    private LocalDateTime revokedAt;
}
