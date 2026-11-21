package com.examsystem.registration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 报名持久化实体；资料快照随报名保存，普通查询不默认读取姓名，HTTP 使用专用投影。 */
@Getter
@Setter
@TableName("registration")
public class Registration {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String registrationNumber;
    private Long candidateId;
    private Long sessionId;
    private Long projectId;
    private Long siteId;

    @TableField(select = false)
    private String candidateNameSnapshot;

    private String candidateTypeSnapshot;
    private Long schoolIdSnapshot;
    private String schoolNameSnapshot;
    private String status;
    private LocalDateTime submittedAt;
}
