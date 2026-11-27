package com.examsystem.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 持久化小程序会话的摘要、账号版本和生命周期。 */
@Getter
@Setter
@TableName("mini_program_session")
public class MiniProgramSession {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long wechatIdentityId;
    private Long sessionVersion;
    private byte[] tokenDigest;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
}
