package com.examsystem.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 角色字典实体；角色代码是既有输入主键，不使用自增或雪花 ID。 */
@Getter
@Setter
@TableName("auth_role")
public class Role {
    @TableId(type = IdType.INPUT)
    private String code;

    private String name;
    private String description;
}
