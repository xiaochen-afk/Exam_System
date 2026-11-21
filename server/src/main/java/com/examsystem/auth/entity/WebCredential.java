package com.examsystem.auth.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Web 凭据实体；不生成字段 toString，密码哈希默认不参与普通查询。 */
@Getter
@Setter
@TableName("web_credential")
public class WebCredential {
    @TableId(type = IdType.INPUT)
    private Long userId;

    private String identifier;

    // MySQL 生成列：允许条件查询，禁止 INSERT/UPDATE 显式写入。
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private String identifierNormalized;

    @TableField(select = false)
    private String passwordHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime passwordChangedAt;
}
