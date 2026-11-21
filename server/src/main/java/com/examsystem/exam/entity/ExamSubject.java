package com.examsystem.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** exam_subject 的持久化实体；仅用于服务层与 Plus CRUD，不直接返回客户端。 */
@Getter
@Setter
@TableName("exam_subject")
public class ExamSubject {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private String name;
    private String description;
    private String status;
}
