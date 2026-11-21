package com.examsystem.portal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 资讯持久化实体。正文仅在详情和编辑相关查询中显式读取。 */
@Getter
@Setter
@TableName("notice")
public class Notice {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String category;
    private String title;
    private String summary;

    @TableField(select = false)
    private String body;

    private String publisherLabel;
    private Boolean pinned;
    private Boolean featured;
    private LocalDateTime publishedAt;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime withdrawnAt;
}
