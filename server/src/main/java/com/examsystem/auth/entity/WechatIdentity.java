package com.examsystem.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 小程序身份按 AppID 隔离；OpenID 仅用于服务端匹配。 */
@Getter
@Setter
@TableName("wechat_identity")
public class WechatIdentity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long candidateId;
    private String appId;
    private byte[] openId;
    private LocalDateTime boundAt;
}
