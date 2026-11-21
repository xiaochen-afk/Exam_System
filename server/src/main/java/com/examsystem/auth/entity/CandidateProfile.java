package com.examsystem.auth.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 考生资料实体；注册时只建立 ID 与更新时间，其余资料保持未提交状态。 */
@Getter
@Setter
@TableName("candidate_profile")
public class CandidateProfile {
    @TableId(type = IdType.INPUT)
    private Long candidateId;

    private String accountKind;
    private String candidateType;
    private Long schoolId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
