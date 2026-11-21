package com.examsystem.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** exam_project 的持久化实体；仅用于服务层与 Plus CRUD，不直接返回客户端。 */
@Getter
@Setter
@TableName("exam_project")
public class ExamProject {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long subjectId;
    private String applicationCode;
    private BigDecimal maxScore;
}
