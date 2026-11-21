package com.examsystem.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 学校基础目录；稳定 ID 用于资料关联和本校推荐，不作为内部授权依据。 */
@Getter
@Setter
@TableName("school")
public class School {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private String name;
    private String status;
}
