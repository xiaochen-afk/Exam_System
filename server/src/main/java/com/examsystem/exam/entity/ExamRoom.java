package com.examsystem.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** exam_room 的持久化实体；仅用于服务层与 Plus CRUD，不直接返回客户端。 */
@Getter
@Setter
@TableName("exam_room")
public class ExamRoom {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long siteId;
    private String name;
    private Integer capacity;
    private String status;
}
