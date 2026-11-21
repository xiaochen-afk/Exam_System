package com.examsystem.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** exam_session 的持久化实体；仅用于服务层与 Plus CRUD，不直接返回客户端。 */
@Getter
@Setter
@TableName("exam_session")
public class ExamSession {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long siteId;
    private String name;
    private String description;
    private LocalDateTime registrationStartsAt;
    private LocalDateTime registrationEndsAt;
    private LocalDateTime examStartsAt;
    private LocalDateTime examEndsAt;
    private Integer capacity;
    private Integer registeredCount;
    private String status;
    private LocalDateTime ticketPublishedAt;
    private Long ticketPublishedBy;
    private LocalDateTime scorePublishedAt;
    private Long scorePublishedBy;
}
