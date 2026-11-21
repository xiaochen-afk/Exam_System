package com.examsystem.portal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 资讯附件元数据；内部存储键默认不查询，也不进入任何公开 DTO。 */
@Getter
@Setter
@TableName("notice_attachment")
public class NoticeAttachment {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long noticeId;

    @TableField(select = false)
    private String storageKey;

    private String fileName;
    private String mediaType;
    private Long byteCount;
}
