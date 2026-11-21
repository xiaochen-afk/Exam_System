package com.examsystem.auth.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.examsystem.auth.model.AuthData;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 账号表实体，仅用于持久化；对外身份和会话快照继续使用明确的 DTO/模型。 */
@Getter
@Setter
@TableName("app_user")
public class UserAccount {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String accountKind;
    private String displayName;
    private String status;
    private Long sessionVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 从数据库实体提取认证所需字段，不让持久化细节进入安全上下文。 */
    public AuthData.User snapshot() {
        return new AuthData.User(id, accountKind, displayName, status, sessionVersion);
    }
}
